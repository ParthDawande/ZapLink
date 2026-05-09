package com.zaplink.util;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeGeneratorTest {

    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Test
    void generatePng_returnsNonEmptyBytes() {
        byte[] png = QrCodeGenerator.generatePng("https://example.com", 300);
        assertThat(png).isNotEmpty();
    }

    @Test
    void generatePng_hasPngSignature() {
        byte[] png = QrCodeGenerator.generatePng("https://example.com", 300);
        assertThat(png).hasSizeGreaterThanOrEqualTo(8);
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            assertThat(png[i]).isEqualTo(PNG_SIGNATURE[i]);
        }
    }

    @Test
    void generatePng_roundTrip_decodesOriginalText() throws Exception {
        String input = "https://example.com/some/path?foo=bar&baz=qux";
        byte[] png = QrCodeGenerator.generatePng(input, 300);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        String decoded = new MultiFormatReader().decode(bitmap).getText();

        assertThat(decoded).isEqualTo(input);
    }
}
