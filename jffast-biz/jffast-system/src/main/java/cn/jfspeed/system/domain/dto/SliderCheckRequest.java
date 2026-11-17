/*
 * Copyright 2024-2026 jffast
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.jfspeed.system.domain.dto;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 滑块验证码校验请求（tianai-captcha 前端 SDK 的 validCaptchaUrl 提交体）。
 *
 * <p>SDK 提交的 JSON 形如 {@code {"id":"...","data":{滑块轨迹}}}，
 * 其中 {@code data} 映射到 {@link ImageCaptchaTrack}。
 *
 * @author jffast
 */
@Data
@Schema(description = "滑块验证码校验请求")
public class SliderCheckRequest {

    @Schema(description = "验证码 id（gen 接口返回）")
    private String id;

    @Schema(description = "滑块轨迹数据")
    private ImageCaptchaTrack data;
}
