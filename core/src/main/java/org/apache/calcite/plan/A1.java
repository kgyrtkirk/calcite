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
package org.apache.calcite.plan;

import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.rules.AggregateCaseToFilterRule.ThreeArgCaseBasedAggregateCallTransform;
import org.apache.calcite.rex.RexNode;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Sample check to ensure that extensions can be written.
 */
public class A1 extends ThreeArgCaseBasedAggregateCallTransform {

  @Override protected @Nullable RexNode transform(LocalAggBuilder localAggBuilder,
      AggregateCall call, RexIf rexIf) {
    return null;
  }

  @Override protected boolean matches(AggregateCall aggregateCall, RexIf rexIf) {
    if (true) {
      throw new RuntimeException("FIXME: Unimplemented!");
    }
    return false;

  }

}
