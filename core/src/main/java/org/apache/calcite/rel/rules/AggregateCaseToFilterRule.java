/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.rel.rules;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlPostfixOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.tools.RelBuilderFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule that converts CASE-style filtered aggregates into true filtered aggregates.
 *
 * <p>For example,
 *
 * <blockquote>
 *   <code>SELECT SUM(CASE WHEN gender = 'F' THEN salary END)<br>
 *   FROM Emp</code>
 * </blockquote>
 *
 * <p>becomes
 *
 * <blockquote>
 *   <code>SELECT SUM(salary) FILTER (WHERE gender = 'F')<br>
 *   FROM Emp</code>
 * </blockquote>
 *
 * @see CoreRules#AGGREGATE_CASE_TO_FILTER
 */
@Value.Enclosing
public class AggregateCaseToFilterRule
    extends RelRule<AggregateCaseToFilterRule.Config>
    implements TransformationRule {

  /** Creates an AggregateCaseToFilterRule. */
  protected AggregateCaseToFilterRule(Config config) {
    super(config);
  }

  @Deprecated // to be removed before 2.0
  protected AggregateCaseToFilterRule(RelBuilderFactory relBuilderFactory,
      String description) {
    this(Config.DEFAULT.withRelBuilderFactory(relBuilderFactory)
        .withDescription(description)
        .as(Config.class));
  }

  @Override public boolean matches(final RelOptRuleCall call) {
    final Aggregate aggregate = call.rel(0);
    final Project project = call.rel(1);

    for (AggregateCall aggregateCall : aggregate.getAggCallList()) {
      final int singleArg = soleArgument(aggregateCall);
      if (singleArg >= 0
          && isThreeArgCase(project.getProjects().get(singleArg))) {
        return true;
      }
    }
    return false;
  }

  @Override public void onMatch(RelOptRuleCall call) {
    final Aggregate aggregate = call.rel(0);
    final Project project = call.rel(1);
    final List<AggregateCall> newCalls =
        new ArrayList<>(aggregate.getAggCallList().size());
    final List<RexNode> newProjects = new ArrayList<>(project.getProjects());

    LocalAggBuilder lab = new LocalAggBuilder(call.builder(), project);

    for (AggregateCall aggregateCall : aggregate.getAggCallList()) {
      lab.add(aggregateCall);
    }

    if (lab.aggs.equals(aggregate.getAggCallList())) {
      // no progress
      return;
    }

    RelNode newRel = lab.build(aggregate);

    call.transformTo(newRel);
    call.getPlanner().prune(aggregate);
  }

  private static class RexIf
  {
    public final RexNode condition;
    public final RexNode left;
    public final RexNode right;

    public RexIf(RexNode condition, RexNode left, RexNode right)
    {
      this.condition = condition;
      this.left = left;
      this.right = right;
    }

    public static RexIf of(RexBuilder rexBuilder, RexNode rexNode)
    {
      if (!isThreeArgCase(rexNode)) {
        return null;
      }
      final RexCall caseCall = (RexCall) rexNode;

      // If one arg is null and the other is not, reverse them and set "flip",
      // which negates the filter.
      final boolean flip = RexLiteral.isNullLiteral(caseCall.operands.get(1))
          && !RexLiteral.isNullLiteral(caseCall.operands.get(2));
      final RexNode arg1 = caseCall.operands.get(flip ? 2 : 1);
      final RexNode arg2 = caseCall.operands.get(flip ? 1 : 2);

      final SqlPostfixOperator op = flip ? SqlStdOperatorTable.IS_NOT_TRUE : SqlStdOperatorTable.IS_TRUE;
      final RexNode filterFromCase = rexBuilder.makeCall(op, caseCall.operands.get(0));

      return new RexIf(filterFromCase, arg1, arg2);
    }
  }

  /**
   * Helper class to aid building the output {@link Aggregate}.
   */
  private static class LocalAggBuilder
  {
    private RelBuilder builder;
    private List<AggregateCall> aggs = new ArrayList<>();
    private List<RexNode> newProjects;
    private Project oldProject;


    public LocalAggBuilder(RelBuilder builder, Project project)
    {
      this.builder = builder;
      this.oldProject = project;
      this.newProjects = new ArrayList<RexNode>(project.getProjects());
    }

    public RelNode build(Aggregate oldAggregate)
    {
      Aggregate aggregate = oldAggregate;
      final RelBuilder relBuilder = builder
          .push(oldProject.getInput())
          .project(newProjects);

      final RelBuilder.GroupKey groupKey =
          relBuilder.groupKey(aggregate.getGroupSet(), aggregate.getGroupSets());

      relBuilder.aggregate(groupKey, aggs)
          .convert(aggregate.getRowType(), false);

      return relBuilder.build();
    }

    public void add(AggregateCall aggregateCall)
    {
      @Nullable
      AggregateCall a = null;

      if (a == null) {
        a = new FilteredDistinctTransform().transform(this, aggregateCall);
      }

      if(a==null) {
        a = transform0(aggregateCall, oldProject, newProjects);
      }

      if(a==null) {
        a=aggregateCall;
      }
      aggs.add(a);
    }

    public RexBuilder getRexBuilder()
    {
      return oldProject.getCluster().getRexBuilder();
    }

    /**
     * Adds the expression to be projected.
     *
     * Returns the index of the projected expression.
     */
    public int projectExpr(RexNode expr)
    {
      newProjects.add(expr);
      return newProjects.size() - 1;
    }

    public int projectCombinedFilter(AggregateCall call, RexNode condition)
    {
      return projectExpr(buildCombinedFilter(call, condition));
    }

    protected RexNode buildCombinedFilter(AggregateCall call, RexNode condition)
    {
      if (call.filterArg < 0) {
        return condition;
      }
      RexNode oldFilterExpr = oldProject.getProjects().get(call.filterArg);
      return RexUtil.composeConjunction(
          getRexBuilder(),
          ImmutableList.of(condition, oldFilterExpr)
      );
    }
  }

  public void onMatch0(RelOptRuleCall call) {
    final Aggregate aggregate = call.rel(0);
    final Project project = call.rel(1);
    final List<AggregateCall> newCalls =
        new ArrayList<>(aggregate.getAggCallList().size());
    final List<RexNode> newProjects = new ArrayList<>(project.getProjects());

    for (AggregateCall aggregateCall : aggregate.getAggCallList()) {
      AggregateCall newCall =
          transform(aggregateCall, project, newProjects);

      if (newCall == null) {
        newCalls.add(aggregateCall);
      } else {
        newCalls.add(newCall);
      }
    }

    if (newCalls.equals(aggregate.getAggCallList())) {
      return;
    }

    final RelBuilder relBuilder = call.builder()
        .push(project.getInput())
        .project(newProjects);

    final RelBuilder.GroupKey groupKey =
        relBuilder.groupKey(aggregate.getGroupSet(), aggregate.getGroupSets());

    relBuilder.aggregate(groupKey, newCalls)
        .convert(aggregate.getRowType(), false);

    call.transformTo(relBuilder.build());
    call.getPlanner().prune(aggregate);
  }



  public static interface AggregateCallTransform {

    public @Nullable AggregateCall transform(LocalAggBuilder localAggBuilder, AggregateCall call);

  }

  protected static abstract class ThreeArgCaseBasedAggregateCallTransform implements AggregateCallTransform {

    public final @Nullable AggregateCall transform(LocalAggBuilder localAggBuilder, AggregateCall call) {

      final int singleArg = soleArgument(call);
      if (singleArg < 0) {
        return null;
      }
      final RexNode rexNode = localAggBuilder.oldProject.getProjects().get(singleArg);
      final RexBuilder rexBuilder = localAggBuilder.getRexBuilder();

      RexIf c = RexIf.of(rexBuilder, rexNode);
      if (c == null) {
        return null;
      }

      return transform(localAggBuilder, call, c);
    }

    protected abstract @Nullable AggregateCall transform(LocalAggBuilder localAggBuilder, AggregateCall call, RexIf rexIf);

  }

  /**
   * Recognizes conditionally filtered distinct.
   *
   * <pre>
   * COUNT(DISTINCT CASE WHEN x = 'foo' THEN y END)
   *  =>
   * COUNT(DISTINCT y) FILTER(WHERE x = 'foo')
   * </pre>
   */
  protected static class FilteredDistinctTransform extends ThreeArgCaseBasedAggregateCallTransform
  {
    @Override
    protected @Nullable AggregateCall transform(LocalAggBuilder localAggBuilder, AggregateCall call, RexIf rexIf)
    {
      if (!call.isDistinct()) {
        return null;
      }
      SqlKind kind = call.getAggregation().getKind();
      if (!(kind == SqlKind.COUNT && RexLiteral.isNullLiteral(rexIf.right))) {
        return null;
      }

      int leftIndex = localAggBuilder.projectExpr(rexIf.left);
      int filterIndex = localAggBuilder.projectCombinedFilter(call, rexIf.condition);
      return AggregateCall.create(
          SqlStdOperatorTable.COUNT, true, false,
          false, call.rexList, ImmutableList.of(leftIndex),
          filterIndex, null, RelCollations.EMPTY,
          call.getType(), call.getName()
      );
    }
  }

  private static RexNode getFilterExpr(AggregateCall call, Project project)
  {
    if (call.filterArg >= 0) {
      return project.getProjects().get(call.filterArg);
    }
    return null;
  }


  public static @Nullable AggregateCall transformX1(LocalAggBuilder localAggBuilder, AggregateCall call)
  {
    final int singleArg = soleArgument(call);
    if (singleArg < 0) {
      return null;
    }
    final RexNode rexNode = localAggBuilder.oldProject.getProjects().get(singleArg);
    final RexBuilder rexBuilder = localAggBuilder.getRexBuilder();

    RexIf c = RexIf.of(rexBuilder, rexNode);
    if (c == null) {
      return null;
    }

    final RexNode combinedFilter = RexUtil.composeConjunction(
        rexBuilder,
        Lists.newArrayList(c.condition, getFilterExpr(call, localAggBuilder.oldProject))
    );

    final SqlKind kind = call.getAggregation().getKind();

    if (call.isDistinct()) {
      // Just one style supported:
      //   COUNT(DISTINCT CASE WHEN x = 'foo' THEN y END)
      // =>
      //   COUNT(DISTINCT y) FILTER(WHERE x = 'foo')

      if (kind == SqlKind.COUNT && RexLiteral.isNullLiteral(c.right)) {
        int leftIndex = localAggBuilder.projectExpr(c.left);
        int filterIndex = localAggBuilder.projectExpr(combinedFilter);
        return AggregateCall.create(SqlStdOperatorTable.COUNT, true, false,
            false, call.rexList, ImmutableList.of(leftIndex),
            filterIndex, null, RelCollations.EMPTY,
            call.getType(), call.getName());
      }
    }
    return null;

  }

  private static @Nullable AggregateCall transform0(AggregateCall call,
      Project project, List<RexNode> newProjects) {
    final int singleArg = soleArgument(call);
    if (singleArg < 0) {
      return null;
    }

    final RexNode rexNode = project.getProjects().get(singleArg);
    final RelOptCluster cluster = project.getCluster();
    final RexBuilder rexBuilder = cluster.getRexBuilder();
    if (false) {
      RexIf c = RexIf.of(rexBuilder, rexNode);
      if (c == null) {
        return null;
      }

      final RexNode filter = RexUtil.composeConjunction(
          rexBuilder,
          ImmutableList.of(c.condition, getFilterExpr(call, project))
      );


      return null;
//      AggregateCall possibleRet = transform2(c, call);
    }

    {

    if (!isThreeArgCase(rexNode)) {
      return null;
    }

    final RexCall caseCall = (RexCall) rexNode;

    // If one arg is null and the other is not, reverse them and set "flip",
    // which negates the filter.
    final boolean flip = RexLiteral.isNullLiteral(caseCall.operands.get(1))
        && !RexLiteral.isNullLiteral(caseCall.operands.get(2));
    final RexNode arg1 = caseCall.operands.get(flip ? 2 : 1);
    final RexNode arg2 = caseCall.operands.get(flip ? 1 : 2);

    // Operand 1: Filter
    final SqlPostfixOperator op =
        flip ? SqlStdOperatorTable.IS_NOT_TRUE : SqlStdOperatorTable.IS_TRUE;
    final RexNode filterFromCase =
        rexBuilder.makeCall(op, caseCall.operands.get(0));

    // Combine the CASE filter with an honest-to-goodness SQL FILTER, if the
    // latter is present.
    final RexNode filter;
    if (call.filterArg >= 0) {
      filter =
          rexBuilder.makeCall(SqlStdOperatorTable.AND,
              project.getProjects().get(call.filterArg),
              filterFromCase);
    } else {
      filter = filterFromCase;
    }

    final SqlKind kind = call.getAggregation().getKind();



    if (call.isDistinct()) {
      // Just one style supported:
      //   COUNT(DISTINCT CASE WHEN x = 'foo' THEN y END)
      // =>
      //   COUNT(DISTINCT y) FILTER(WHERE x = 'foo')

      if (kind == SqlKind.COUNT
          && RexLiteral.isNullLiteral(arg2)) {
        newProjects.add(arg1);
        newProjects.add(filter);
        return AggregateCall.create(SqlStdOperatorTable.COUNT, true, false,
            false, call.rexList, ImmutableList.of(newProjects.size() - 2),
            newProjects.size() - 1, null, RelCollations.EMPTY,
            call.getType(), call.getName());
      }
      return null;
    }

    // Four styles supported:
    //
    // A1: AGG(CASE WHEN x = 'foo' THEN expr END)
    //   => AGG(expr) FILTER (x = 'foo')
    // A2: SUM0(CASE WHEN x = 'foo' THEN cnt ELSE 0 END)
    //   => SUM0(cnt) FILTER (x = 'foo')
    // B: SUM0(CASE WHEN x = 'foo' THEN 1 ELSE 0 END)
    //   => COUNT() FILTER (x = 'foo')
    // C: COUNT(CASE WHEN x = 'foo' THEN 'dummy' END)
    //   => COUNT() FILTER (x = 'foo')

    final SqlParserPos pos = call.getParserPosition();
    if (kind == SqlKind.COUNT // Case C
        && arg1.isA(SqlKind.LITERAL)
        && !RexLiteral.isNullLiteral(arg1)
        && RexLiteral.isNullLiteral(arg2)) {
      newProjects.add(filter);
      return AggregateCall.create(pos, SqlStdOperatorTable.COUNT, false, false,
          false, call.rexList, ImmutableList.of(), newProjects.size() - 1, null,
          RelCollations.EMPTY, call.getType(),
          call.getName());
    } else if (kind == SqlKind.SUM0 // Case B
        && isIntLiteral(arg1, BigDecimal.ONE)
        && isIntLiteral(arg2, BigDecimal.ZERO)) {

      newProjects.add(filter);
      final RelDataTypeFactory typeFactory = cluster.getTypeFactory();
      final RelDataType dataType =
          typeFactory.createTypeWithNullability(
              typeFactory.createSqlType(SqlTypeName.BIGINT), false);
      return AggregateCall.create(pos, SqlStdOperatorTable.COUNT, false, false,
          false, call.rexList, ImmutableList.of(), newProjects.size() - 1, null,
          RelCollations.EMPTY, dataType, call.getName());
    } else if ((RexLiteral.isNullLiteral(arg2) // Case A1
            && call.getAggregation().allowsFilter())
        || (kind == SqlKind.SUM0 // Case A2
            && isIntLiteral(arg2, BigDecimal.ZERO))) {
      newProjects.add(arg1);
      newProjects.add(filter);
      return AggregateCall.create(pos, call.getAggregation(), false,
          false, false, call.rexList, ImmutableList.of(newProjects.size() - 2),
          newProjects.size() - 1, null, RelCollations.EMPTY,
          call.getType(), call.getName());
    } else {
      return null;
    }
    }
  }

  private static @Nullable AggregateCall transform(AggregateCall call,
      Project project, List<RexNode> newProjects) {
    final int singleArg = soleArgument(call);
    if (singleArg < 0) {
      return null;
    }

    final RexNode rexNode = project.getProjects().get(singleArg);
    if (!isThreeArgCase(rexNode)) {
      return null;
    }

    final RelOptCluster cluster = project.getCluster();
    final RexBuilder rexBuilder = cluster.getRexBuilder();
    final RexCall caseCall = (RexCall) rexNode;

    // If one arg is null and the other is not, reverse them and set "flip",
    // which negates the filter.
    final boolean flip = RexLiteral.isNullLiteral(caseCall.operands.get(1))
        && !RexLiteral.isNullLiteral(caseCall.operands.get(2));
    final RexNode arg1 = caseCall.operands.get(flip ? 2 : 1);
    final RexNode arg2 = caseCall.operands.get(flip ? 1 : 2);

    // Operand 1: Filter
    final SqlPostfixOperator op =
        flip ? SqlStdOperatorTable.IS_NOT_TRUE : SqlStdOperatorTable.IS_TRUE;
    final RexNode filterFromCase =
        rexBuilder.makeCall(op, caseCall.operands.get(0));

    // Combine the CASE filter with an honest-to-goodness SQL FILTER, if the
    // latter is present.
    final RexNode filter;
    if (call.filterArg >= 0) {
      filter =
          rexBuilder.makeCall(SqlStdOperatorTable.AND,
              project.getProjects().get(call.filterArg),
              filterFromCase);
    } else {
      filter = filterFromCase;
    }

    final SqlKind kind = call.getAggregation().getKind();
    if (call.isDistinct()) {
      // Just one style supported:
      //   COUNT(DISTINCT CASE WHEN x = 'foo' THEN y END)
      // =>
      //   COUNT(DISTINCT y) FILTER(WHERE x = 'foo')

      if (kind == SqlKind.COUNT
          && RexLiteral.isNullLiteral(arg2)) {
        newProjects.add(arg1);
        newProjects.add(filter);
        return AggregateCall.create(
            call.getParserPosition(), SqlStdOperatorTable.COUNT, true, false,
            false, call.rexList, ImmutableList.of(newProjects.size() - 2),
            newProjects.size() - 1, null, RelCollations.EMPTY,
            call.getType(), call.getName());
      }
      return null;
    }

    // Four styles supported:
    //
    // A1: AGG(CASE WHEN x = 'foo' THEN expr END)
    //   => AGG(expr) FILTER (x = 'foo')
    // A2: SUM0(CASE WHEN x = 'foo' THEN cnt ELSE 0 END)
    //   => SUM0(cnt) FILTER (x = 'foo')
    // B: SUM0(CASE WHEN x = 'foo' THEN 1 ELSE 0 END)
    //   => COUNT() FILTER (x = 'foo')
    // C: COUNT(CASE WHEN x = 'foo' THEN 'dummy' END)
    //   => COUNT() FILTER (x = 'foo')

    final SqlParserPos pos = call.getParserPosition();
    if (kind == SqlKind.COUNT // Case C
        && arg1.isA(SqlKind.LITERAL)
        && !RexLiteral.isNullLiteral(arg1)
        && RexLiteral.isNullLiteral(arg2)) {
      newProjects.add(filter);
      return AggregateCall.create(pos, SqlStdOperatorTable.COUNT, false, false,
          false, call.rexList, ImmutableList.of(), newProjects.size() - 1, null,
          RelCollations.EMPTY, call.getType(),
          call.getName());
    } else if (kind == SqlKind.SUM0 // Case B
        && isIntLiteral(arg1, BigDecimal.ONE)
        && isIntLiteral(arg2, BigDecimal.ZERO)) {

      newProjects.add(filter);
      final RelDataTypeFactory typeFactory = cluster.getTypeFactory();
      final RelDataType dataType =
          typeFactory.createTypeWithNullability(
              typeFactory.createSqlType(SqlTypeName.BIGINT), false);
      return AggregateCall.create(pos, SqlStdOperatorTable.COUNT, false, false,
          false, call.rexList, ImmutableList.of(), newProjects.size() - 1, null,
          RelCollations.EMPTY, dataType, call.getName());
    } else if ((RexLiteral.isNullLiteral(arg2) // Case A1
            && call.getAggregation().allowsFilter())
        || (kind == SqlKind.SUM0 // Case A2
            && isIntLiteral(arg2, BigDecimal.ZERO))) {
      newProjects.add(arg1);
      newProjects.add(filter);
      return AggregateCall.create(pos, call.getAggregation(), false,
          false, false, call.rexList, ImmutableList.of(newProjects.size() - 2),
          newProjects.size() - 1, null, RelCollations.EMPTY,
          call.getType(), call.getName());
    } else {
      return null;
    }
  }

  /** Returns the argument, if an aggregate call has a single argument,
   * otherwise -1. */
  private static int soleArgument(AggregateCall aggregateCall) {
    return aggregateCall.getArgList().size() == 1
        ? aggregateCall.getArgList().get(0)
        : -1;
  }

  private static boolean isThreeArgCase(final RexNode rexNode) {
    return rexNode.getKind() == SqlKind.CASE
        && ((RexCall) rexNode).operands.size() == 3;
  }

  private static boolean isIntLiteral(RexNode rexNode, BigDecimal value) {
    return rexNode instanceof RexLiteral
        && SqlTypeName.INT_TYPES.contains(rexNode.getType().getSqlTypeName())
        && value.equals(((RexLiteral) rexNode).getValueAs(BigDecimal.class));
  }

  /** Rule configuration. */
  @Value.Immutable
  public interface Config extends RelRule.Config {
    Config DEFAULT = ImmutableAggregateCaseToFilterRule.Config.of()
        .withOperandSupplier(b0 ->
            b0.operand(Aggregate.class).oneInput(b1 ->
                b1.operand(Project.class).anyInputs()));


    @Override default AggregateCaseToFilterRule toRule() {
      return new AggregateCaseToFilterRule(this);
    }
  }
}
