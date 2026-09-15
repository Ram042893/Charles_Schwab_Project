package com.schwab.shortener.orchestration.governance;

import com.schwab.shortener.common.BusinessException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PolicyGuardrail {

    private static final Pattern SECRET = Pattern.compile("(?i)(password|secret|api[_-]?key|token)\\s*[:=]\\s*\\S+");

    public void assertNoSecrets(String text) {
        if (text != null && SECRET.matcher(text).find()) {
            throw new BusinessException(422, "Policy violation: secrets must not appear in requirements or artifacts");
        }
    }
}
