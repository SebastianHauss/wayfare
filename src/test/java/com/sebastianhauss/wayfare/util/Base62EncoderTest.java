package com.sebastianhauss.wayfare.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class Base62EncoderTest {

    @Test
    void encode_zero_returnsZeroChar() {
        assertThat(Base62Encoder.encode(0L)).isEqualTo("0");
    }

    @Test
    void encode_singleDigitValues() {
        assertThat(Base62Encoder.encode(5L)).isEqualTo("5");
        assertThat(Base62Encoder.encode(9L)).isEqualTo("9");
        assertThat(Base62Encoder.encode(10L)).isEqualTo("a");
        assertThat(Base62Encoder.encode(35L)).isEqualTo("z");
        assertThat(Base62Encoder.encode(36L)).isEqualTo("A");
        assertThat(Base62Encoder.encode(61L)).isEqualTo("Z");
    }

    @Test
    void encode_rollsOverToTwoDigits_at62() {
        assertThat(Base62Encoder.encode(62L)).isEqualTo("10");
        assertThat(Base62Encoder.encode(63L)).isEqualTo("11");
        assertThat(Base62Encoder.encode(123L)).isEqualTo("1Z");
    }

    @Test
    void encode_isUniquePerId() {
        Set<String> codes = new HashSet<>();
        LongStream.range(0, 10_000).forEach(id -> codes.add(Base62Encoder.encode(id)));

        assertThat(codes).hasSize(10_000);
    }
}
