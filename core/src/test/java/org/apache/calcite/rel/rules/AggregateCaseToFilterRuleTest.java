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

import com.google.common.collect.Iterables;
import org.apache.calcite.adapter.enumerable.EnumerableRules;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.SqlExplainFormat;
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.tools.RuleSet;
import org.apache.calcite.tools.RuleSets;
import org.apache.calcite.util.Util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public final class AggregateCaseToFilterRuleTest
{

  @Test
  void t1()
  {
    String sql = ""
        + "select count(case when \"deptno\" > 1 then 71 end) from \"hr\".\"emps\"";
    new Fixture(sql, CoreRules.AGGREGATE_CASE_TO_FILTER)
        .assertThatPlan(
            allOf(
                containsString("COUNT() FILTER $0"),
                not(containsString("CASE"))
            )
        );
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2554">[CALCITE-2554]
   * Enrich enumerable join operators with order preserving information</a>.
   *
   * <p>
   * Since join inputs are sorted, and this join preserves the order of the left
   * input, there shouldn't be any sort operator above the join.
   */
  @Test
  void removeSortOverEnumerableNestedLoopJoin()
  {
    RuleSet prepareRules = RuleSets.ofList(
        CoreRules.SORT_PROJECT_TRANSPOSE,
        EnumerableRules.ENUMERABLE_JOIN_RULE,
        EnumerableRules.ENUMERABLE_PROJECT_RULE,
        EnumerableRules.ENUMERABLE_SORT_RULE,
        EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE
    );
    // Inner join is not considered since the ENUMERABLE_JOIN_RULE does not
    // generate a nestedLoop
    // join in the case of inner joins.
    for (String joinType : Arrays.asList("left", "right", "full")) {
      String sql = "select e.\"deptno\" from \"hr\".\"emps\" e "
          + joinType + " join \"hr\".\"depts\" d "
          + " on e.\"deptno\" > d.\"deptno\" "
          + "order by e.\"empid\" ";
      new Fixture(sql, prepareRules)
          .assertThatPlan(
              allOf(
                  containsString("EnumerableNestedLoopJoin"),
                  not(containsString("EnumerableSort"))
              )
          );
    }
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2554">[CALCITE-2554]
   * Enrich enumerable join operators with order preserving information</a>.
   *
   * <p>
   * Since join inputs are sorted, and this join preserves the order of the left
   * input, there shouldn't be any sort operator above the join.
   *
   * <p>
   * Until CALCITE-2018 is fixed we can add back
   * EnumerableRules.ENUMERABLE_SORT_RULE
   */
  @Test
  void removeSortOverEnumerableCorrelate() throws Exception
  {
    RuleSet prepareRules = RuleSets.ofList(
        CoreRules.SORT_PROJECT_TRANSPOSE,
        CoreRules.JOIN_TO_CORRELATE,
        EnumerableRules.ENUMERABLE_PROJECT_RULE,
        EnumerableRules.ENUMERABLE_CORRELATE_RULE,
        EnumerableRules.ENUMERABLE_FILTER_RULE,
        EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE
    );
    for (String joinType : Arrays.asList("left", "inner")) {
      String sql = "select e.\"deptno\" from \"hr\".\"emps\" e "
          + joinType + " join \"hr\".\"depts\" d "
          + " on e.\"deptno\" = d.\"deptno\" "
          + "order by e.\"empid\" ";
      RelNode actualPlan = new Fixture(sql, prepareRules).plan();
      assertThat(
          toString(actualPlan),
          allOf(
              containsString("EnumerableCorrelate"),
              not(containsString("EnumerableSort"))
          )
      );
    }
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2554">[CALCITE-2554]
   * Enrich enumerable join operators with order preserving information</a>.
   *
   * <p>
   * Since join inputs are sorted, and this join preserves the order of the left
   * input, there shouldn't be any sort operator above the join.
   */
  @Test
  void removeSortOverEnumerableSemiJoin() throws Exception
  {
    RuleSet prepareRules = RuleSets.ofList(
        CoreRules.SORT_PROJECT_TRANSPOSE,
        CoreRules.PROJECT_TO_SEMI_JOIN,
        CoreRules.JOIN_TO_SEMI_JOIN,
        EnumerableRules.ENUMERABLE_PROJECT_RULE,
        EnumerableRules.ENUMERABLE_SORT_RULE,
        EnumerableRules.ENUMERABLE_JOIN_RULE,
        EnumerableRules.ENUMERABLE_FILTER_RULE,
        EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE
    );
    String sql = "select e.\"deptno\" from \"hr\".\"emps\" e\n"
        + " where e.\"deptno\" in (select d.\"deptno\" from \"hr\".\"depts\" d)\n"
        + " order by e.\"empid\"";
    RelNode actualPlan = new Fixture(sql, prepareRules)
        .withSqlToRel(c -> c.withExpand(true))
        .plan();
    assertThat(
        toString(actualPlan),
        allOf(
            containsString("EnumerableHashJoin"),
            not(containsString("EnumerableSort"))
        )
    );
  }

  private static String toString(RelNode rel)
  {
    return Util.toLinux(
        RelOptUtil.dumpPlan(
            "", rel, SqlExplainFormat.TEXT,
            SqlExplainLevel.DIGEST_ATTRIBUTES
        )
    );
  }

  static class Fixture extends SortRemoveRuleTest.Fixture
  {

    Fixture(String sql, RuleSet prepareRules)
    {
      super(sql, prepareRules);
    }

    public Fixture(String sql, RelOptRule... rules)
    {
      this(sql, buildRuleSet(rules));
    }

    private static RuleSet buildRuleSet(RelOptRule... rules)
    {
      return RuleSets.ofList(Iterables.concat(Arrays.asList(rules), EnumerableRules.ENUMERABLE_RULES));
    }
  }

}
