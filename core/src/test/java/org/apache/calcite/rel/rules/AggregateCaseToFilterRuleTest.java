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

import org.apache.calcite.adapter.enumerable.EnumerableRules;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.tools.RuleSet;
import org.apache.calcite.tools.RuleSets;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

/**
 * Some apidoc.
 */
public final class AggregateCaseToFilterRuleTest {

  @Test void t1() {
    String sql = ""
        + "select count(case when \"deptno\" > 1 then 71 end) from \"hr\".\"emps\"";
    new Fixture(sql,
        AggregateCaseToFilterRule.Config.DEFAULT
            .withTransforms(ImmutableList.of()).toRule())
                .assertThatPlan(
                    allOf(not(containsString("COUNT() FILTER $0")),
                    containsString("CASE")));
  }

  @Test void t11() {
    String sql = ""
        + "select count(case when \"deptno\" > 1 then 71 end) from \"hr\".\"emps\"";
    new Fixture(sql, AggregateCaseToFilterRule.Config.DEFAULT.toRule())
        .assertThatPlan(
            allOf(containsString("COUNT() FILTER $0"),
            not(containsString("CASE"))));
  }

  /**
   * Apidoc.
   */
  static class Fixture extends SortRemoveRuleTest.Fixture {

    Fixture(String sql, RuleSet prepareRules) {
      super(sql, prepareRules);
    }

    Fixture(String sql, RelOptRule... rules) {
      this(sql, buildRuleSet(rules));
    }

    private static RuleSet buildRuleSet(RelOptRule... rules) {
      return RuleSets.ofList(
          Iterables.concat(Arrays.asList(rules),
          EnumerableRules.ENUMERABLE_RULES));
    }
  }

}
