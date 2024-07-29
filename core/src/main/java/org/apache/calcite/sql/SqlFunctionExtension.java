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
package org.apache.calcite.sql;

/**
 * Parent class for Sql function extensions.
 *
 * Extensions of this interface might be hooked into the parser or other parts of the project.
 * Main intention is to enable flexible addition of features not mandatorily specified for all funcitons.
 *
 * Examples could be:
 * <ul>
 *   <li>customized parser features
 *   <li>custom validation logic for some function. FIXME write more
 * </ul>
 */
public interface SqlFunctionExtension {
}
