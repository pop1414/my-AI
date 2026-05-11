package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.result.CurrentUserResult;
import io.github.spike.myai.auth.application.result.CurrentUserCapabilitiesResult;

/**
 * 获取当前用户能力位用例。
 */
public interface GetCurrentUserCapabilitiesUseCase {

    CurrentUserCapabilitiesResult resolve(CurrentUserResult result);
}
