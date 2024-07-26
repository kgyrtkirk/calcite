package org.apache.calcite.sql;

import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface AggregateParamsValidator extends SqlAggFunctionExtension {

    void validateAggregateParams(
SqlValidator validator, SqlAggFunction op,
        SqlCall aggCall,
            @Nullable SqlNode filter, @Nullable SqlNodeList distinctList,
            @Nullable SqlNodeList orderList, SqlValidatorScope scope);

}