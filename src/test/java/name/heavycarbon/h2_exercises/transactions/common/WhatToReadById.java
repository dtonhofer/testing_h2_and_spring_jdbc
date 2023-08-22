package name.heavycarbon.h2_exercises.transactions.common;

import lombok.EqualsAndHashCode;
import lombok.Value;
import name.heavycarbon.h2_exercises.transactions.db.StuffId;

@Value
@EqualsAndHashCode(callSuper = false)
public class WhatToReadById extends WhatToRead {

    StuffId id;

}
