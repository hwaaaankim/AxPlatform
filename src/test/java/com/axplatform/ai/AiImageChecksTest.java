package com.axplatform.ai;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

class AiImageChecksTest {
    private byte[] png() throws Exception {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
    @Test void validPngAndTextPassWithoutProviderRequests() throws Exception {AiImageChecks.validate(new OpenAiResponses.Source("정상.png","image/png",png()));AiImageChecks.validate(new OpenAiResponses.Source("자료.txt","text/plain","data".getBytes()));}
    @Test void brokenImageNamesTheFileAndRemedy(){assertThatThrownBy(()->AiImageChecks.validate(new OpenAiResponses.Source("손상.jpg","image/jpeg",new byte[]{1,2,3}))).isInstanceOf(OpenAiResponses.Failure.class).hasMessageContaining("손상.jpg").hasMessageContaining("다시 저장");}
    @Test void oversizedPixelDimensionsRejectBeforeAllocatingBitmap() throws Exception {byte[] huge=png();ByteBuffer.wrap(huge,16,8).putInt(6000).putInt(6000);var crc=new java.util.zip.CRC32();crc.update(huge,12,17);ByteBuffer.wrap(huge,29,4).putInt((int)crc.getValue());assertThatThrownBy(()->AiImageChecks.validate(new OpenAiResponses.Source("큰사진.png","image/png",huge))).isInstanceOf(OpenAiResponses.Failure.class).hasMessageContaining("2,500만");}
}
