package org.apache.calcite.rel.core;

import org.apache.calcite.sql.SqlAggFunctionExtension;
import org.codehaus.commons.nullanalysis.NotNull;

public interface AggCallBindingFactory extends SqlAggFunctionExtension {
    @NotNull
    Aggregate.AggCallBinding createAggCallBinding(AggregateCall aggregateCall,
            Aggregate aggregateRelBase);

}
