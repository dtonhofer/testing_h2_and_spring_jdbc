package name.heavycarbon.h2_exercises.transactions.common;

import name.heavycarbon.h2_exercises.transactions.db.Stuff;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class ListOfStuffHandling {

    public enum WhatDo { Check, Assert }

    public static void assertListEquality(List<Stuff> actual, List<Stuff> expected) {
        Assertions.assertThat(Stuff.sortById(actual)).isEqualTo(Stuff.sortById(expected));
    }

    public static boolean checkListEquality(@NotNull List<Stuff> a, @NotNull Stuff b) {
        return Stuff.sortById(a).equals(List.of(b));
    }

    public static boolean checkListEquality(List<Stuff> actual, List<Stuff> expected) {
        return Stuff.sortById(actual).equals(Stuff.sortById(expected));
    }

}
