package io.github.teemuki8.libgdx.agent.gameplay.core.prefab;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Bounded JSON values retained only while an explicit component codec runs. */
public sealed interface PrefabValue permits
        PrefabValue.StringValue,
        PrefabValue.NumberValue,
        PrefabValue.BooleanValue,
        PrefabValue.ArrayValue,
        PrefabValue.ObjectValue,
        PrefabValue.NullValue {
    /** JSON pointer for this value. */
    String pointer();

    /** One-based source line. */
    long line();

    /** One-based source column. */
    long column();

    /** String value. */
    record StringValue(String value, String pointer, long line, long column)
            implements PrefabValue {
    }

    /** Exact decimal token and parsed decimal value. */
    record NumberValue(String token, BigDecimal value, String pointer, long line, long column)
            implements PrefabValue {
    }

    /** Boolean value. */
    record BooleanValue(boolean value, String pointer, long line, long column)
            implements PrefabValue {
    }

    /** Ordered bounded array value. */
    record ArrayValue(List<PrefabValue> values, String pointer, long line, long column)
            implements PrefabValue {
        /** Defensively copies values. */
        public ArrayValue {
            values = List.copyOf(values);
        }
    }

    /** Key-sorted bounded object value. */
    record ObjectValue(Map<String, PrefabValue> values, String pointer, long line, long column)
            implements PrefabValue {
        /** Defensively copies values. */
        public ObjectValue {
            values = Collections.unmodifiableMap(new TreeMap<>(values));
        }
    }

    /** Explicit JSON null value, rejected by standard codecs. */
    record NullValue(String pointer, long line, long column) implements PrefabValue {
    }
}
