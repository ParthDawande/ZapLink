package com.zaplink.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    @Test
    void encode_id1_returns1() {
        assertEquals("1", Base62Encoder.encode(1));
    }

    @Test
    void encode_id61_returnsZ() {
        assertEquals("Z", Base62Encoder.encode(61));
    }

    @Test
    void encode_id62_returns10() {
        assertEquals("10", Base62Encoder.encode(62));
    }

    @Test
    void encode_id12345_returns3d7() {
        assertEquals("3d7", Base62Encoder.encode(12345));
    }

    @Test
    void encode_id999999_returns4c91() {
        assertEquals("4c91", Base62Encoder.encode(999999));
    }

    @Test
    void roundTrip_id1() {
        assertEquals(1L, Base62Encoder.decode(Base62Encoder.encode(1)));
    }

    @Test
    void roundTrip_id62() {
        assertEquals(62L, Base62Encoder.decode(Base62Encoder.encode(62)));
    }

    @Test
    void roundTrip_id12345() {
        assertEquals(12345L, Base62Encoder.decode(Base62Encoder.encode(12345)));
    }

    @Test
    void roundTrip_id999999() {
        assertEquals(999999L, Base62Encoder.decode(Base62Encoder.encode(999999)));
    }

    @Test
    void roundTrip_maxInt() {
        long val = Integer.MAX_VALUE;
        assertEquals(val, Base62Encoder.decode(Base62Encoder.encode(val)));
    }

    @Test
    void encode_throwsOnZero() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.encode(0));
    }

    @Test
    void encode_throwsOnNegative() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.encode(-1));
    }

    @Test
    void decode_throwsOnInvalidChar() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.decode("!invalid"));
    }

    @Test
    void decode_throwsOnEmpty() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.decode(""));
    }
}
