package com.axplatform.ai;

import com.axplatform.common.JsonCodec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Provider diagnostics contain protocol metadata only, never response messages or input. */
final class AiProviderErrors {
    private static final Logger log = LoggerFactory.getLogger(AiProviderErrors.class);

    private AiProviderErrors() {}

    static OpenAiResponses.Failure response(
            HttpResponse<byte[]> response,
            String suffix,
            String model,
            String key,
            JsonCodec json) {
        Map<?, ?> error = Map.of();
        try {
            Object parsed =
                    json.object(new String(response.body(), StandardCharsets.UTF_8)).get("error");
            if (parsed instanceof Map<?, ?> map) error = map;
        } catch (RuntimeException ignored) {
            /* An HTML gateway response must never reach logs. */
        }
        int status = response.statusCode();
        String providerCode = atom(error.get("code"), key);
        String type = atom(error.get("type"), key);
        String param = atom(error.get("param"), key);
        String requestId =
                response.headers() == null
                        ? ""
                        : atom(response.headers().firstValue("x-request-id").orElse(""), key);
        String code, message;
        if (providerCode.equals("invalid_api_key")) {
            code = "AI_PROVIDER_INVALID_KEY";
            message = "OpenAI가 현재 API 키를 유효하지 않은 키로 거부했습니다. 관리자가 새 키를 개인 설정에 반영하고 서버를 재시작해야 합니다.";
        } else if (status == 401) {
            code = "AI_PROVIDER_AUTH";
            message = "OpenAI 인증에 실패했습니다. 관리자가 API 키와 연결된 프로젝트를 확인해 주세요.";
        } else if (providerCode.equals("misalignment_policy_violation")) {
            code = "AI_PROVIDER_POLICY";
            message = "OpenAI 정책으로 요청이 차단되었습니다. 이 요청을 자동 재시도하지 않습니다. 관리자가 요청 기록을 확인해야 합니다.";
        } else if (status == 403) {
            code = "AI_PROVIDER_PERMISSION";
            message = "OpenAI가 이 요청의 접근을 허용하지 않았습니다. 관리자가 키의 API 접근 권한·프로젝트·지역 제한을 확인해 주세요.";
        } else if (providerCode.equals("model_not_found")) {
            code = "AI_MODEL_UNAVAILABLE";
            message = "설정한 모델을 이 API 프로젝트에서 사용할 수 없습니다. 관리자가 모델 이름과 프로젝트의 모델 접근 권한을 확인해 주세요.";
        } else if (status == 404) {
            code = "AI_PROVIDER_NOT_FOUND";
            message = "OpenAI 요청 대상이 확인되지 않았습니다. 관리자가 요청 경로와 모델 설정을 확인해 주세요.";
        } else if (Set.of(
                                "insufficient_quota",
                                "billing_hard_limit_reached",
                                "billing_not_active",
                                "billing_limit_usd",
                                "usage_limit_reached",
                                "spend_limit_exceeded")
                        .contains(providerCode)
                || type.equals("insufficient_quota")) {
            code = "AI_PROVIDER_BILLING";
            message = "OpenAI API 잔액 또는 사용 예산 한도로 요청이 거부되었습니다. 관리자가 API 결제·잔액·한도를 확인해야 합니다.";
        } else if (status == 429
                && Set.of("rate_limit_exceeded", "slow_down").contains(providerCode)) {
            code = "AI_PROVIDER_RATE_LIMIT";
            message = "OpenAI 요청 속도 한도에 도달했습니다. 잠시 기다린 뒤 직접 다시 요청해 주세요. 자동 재시도하지 않았습니다.";
        } else if (status == 429) {
            code = "AI_PROVIDER_LIMIT";
            message = "OpenAI 사용량 또는 요청 한도로 중단했습니다. 관리자 확인 후 다시 요청해 주세요. 자동 재시도하지 않았습니다.";
        } else if (Set.of("invalid_image", "invalid_image_format", "invalid_image_url", "image_parse_error", "invalid_base64_image", "unsupported_image").contains(providerCode)) {
            code = "AI_IMAGE_REJECTED";
            message = "OpenAI가 사진 형식을 읽지 못했습니다. 원본을 사진 앱에서 PNG/JPG로 다시 저장하고 표 부분을 잘라 올려 주세요. 해당 사진의 시트를 다시 선택해 요청할 수 있습니다.";
        } else if (Set.of("image_too_large", "image_too_small").contains(providerCode)) {
            code = "AI_IMAGE_DIMENSIONS";
            message = providerCode.equals("image_too_large") ? "사진이 제공자의 이미지 크기 한도를 넘었습니다. 표를 나누어 자르거나 해상도를 줄여 다시 올려 주세요." : "사진이 너무 작아 제공자가 읽지 못했습니다. 글자가 선명하게 보이도록 더 높은 해상도로 촬영해 주세요.";
        } else if (status == 400 || status == 422) {
            code = "AI_PROVIDER_REQUEST";
            message = "OpenAI가 요청 설정 또는 입력 형식을 거부했습니다. 관리자에게 오류 확인 정보를 전달해 주세요.";
        } else {
            code = status >= 500 ? "AI_PROVIDER_UNAVAILABLE" : "AI_PROVIDER_REJECTED";
            message =
                    status >= 500
                            ? "OpenAI가 현재 요청을 처리하지 못했습니다. 잠시 뒤 직접 다시 요청해 주세요. 자동 재시도하지 않았습니다."
                            : "OpenAI가 요청을 거부했습니다. 관리자에게 오류 확인 정보를 전달해 주세요.";
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("stage", suffix.isEmpty() ? "GENERATION" : "INPUT_TOKENS");
        details.put("endpoint", "/v1/responses" + suffix);
        details.put("httpStatus", status);
        details.put("providerCode", providerCode);
        details.put("providerType", type);
        details.put("parameter", param);
        details.put("requestId", requestId);
        details.put("model", atom(model, key));
        log.warn(
                "OpenAI rejected stage={} http={} code={} type={} param={} requestId={} model={}",
                details.get("stage"),
                status,
                providerCode,
                type,
                param,
                requestId,
                details.get("model"));
        return new OpenAiResponses.Failure(code, message, details);
    }

    private static String atom(Object value, String key) {
        if (!(value instanceof String text) || !text.matches("[a-zA-Z0-9_.:/\\[\\]-]{1,160}"))
            return "";
        if (text.toLowerCase(Locale.ROOT).contains("sk-")
                || text.toLowerCase(Locale.ROOT).contains("bearer")
                || (key != null && !key.isBlank() && text.contains(key))) return "";
        return text;
    }
}
