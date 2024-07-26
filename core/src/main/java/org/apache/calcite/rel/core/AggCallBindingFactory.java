package org.apache.calcite.rel.core;

import java.util.List;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.SqlAggFunction;

public interface AggCallBindingFactory {

    Aggregate.AggCallBinding AggCallBinding(RelDataTypeFactory typeFactory,
            SqlAggFunction aggFunction, List<RelDataType> preOperands,
            List<RelDataType> operands, int groupCount,
            boolean filter);

}
