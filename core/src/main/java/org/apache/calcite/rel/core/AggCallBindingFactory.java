package org.apache.calcite.rel.core;

import org.codehaus.commons.nullanalysis.NotNull;

public interface AggCallBindingFactory {
    @NotNull
    Aggregate.AggCallBinding createAggCallBinding(AggregateCall aggregateCall,
            Aggregate aggregateRelBase);

}
