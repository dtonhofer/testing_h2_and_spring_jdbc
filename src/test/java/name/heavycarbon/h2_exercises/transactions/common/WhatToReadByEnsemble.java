package name.heavycarbon.h2_exercises.transactions.common;

import lombok.EqualsAndHashCode;
import lombok.Value;
import name.heavycarbon.h2_exercises.transactions.db.EnsembleId;

@Value
@EqualsAndHashCode(callSuper = false)
public class WhatToReadByEnsemble extends WhatToRead {

    EnsembleId ensembleId;

}
