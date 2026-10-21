package com.axplatform.ai;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

final class AiImageChecks {
    private AiImageChecks() {}
    static void validate(OpenAiResponses.Source source) {
        if(!source.mediaType().startsWith("image/"))return;
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(source.data()))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IllegalArgumentException();
            var reader=readers.next();
            try { reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1)throw new IllegalArgumentException();
                if((long)width*height>25_000_000L)throw new OpenAiResponses.Failure("AI_IMAGE_TOO_LARGE","‘"+source.name()+"’은 2,500만 픽셀을 초과합니다. 표 부분을 나누어 자르거나 해상도를 줄여 다시 올려 주세요.");
            } finally {reader.dispose();}
        } catch(OpenAiResponses.Failure error){throw error;}
        catch(Exception error){throw new OpenAiResponses.Failure("AI_IMAGE_INVALID","‘"+source.name()+"’의 이미지 구조를 읽지 못했습니다. 파일이 손상되었거나 실제 PNG/JPG 형식이 아닐 수 있습니다. 사진 앱에서 PNG/JPG로 다시 저장해 올려 주세요.");}
    }
}
