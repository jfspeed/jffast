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
package cn.jfspeed.job.scanner;

import cn.jfspeed.job.annotation.JfFastJob;
import cn.jfspeed.job.domain.JobTargetInfo;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author jffast
 */
public class JobAnnotationScanner implements BeanPostProcessor {

    private final List<JobTargetInfo> jobTargets = new CopyOnWriteArrayList<>();

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
        ReflectionUtils.doWithMethods(bean.getClass(), method -> {
            JfFastJob annotation = method.getAnnotation(JfFastJob.class);
            if (annotation != null) {
                JobTargetInfo info = new JobTargetInfo();
                info.setBeanName(beanName);
                info.setMethodName(method.getName());
                info.setName(annotation.name());

                List<String> paramNames = Collections.emptyList();
                if (StringUtils.hasText(annotation.params())) {
                    paramNames = Arrays.stream(annotation.params().split(","))
                            .map(String::trim)
                            .filter(StringUtils::hasText)
                            .collect(Collectors.toList());
                }
                info.setParamNames(paramNames);

                if (paramNames.isEmpty()) {
                    info.setInvokeTarget(beanName + "." + method.getName());
                } else {
                    String paramsStr = paramNames.stream()
                            .map(p -> "{" + p + "}")
                            .collect(Collectors.joining(","));
                    info.setInvokeTarget(beanName + "." + method.getName() + "(" + paramsStr + ")");
                }

                jobTargets.add(info);
            }
        });
        return bean;
    }

    public List<JobTargetInfo> getJobTargets() {
        return new ArrayList<>(jobTargets);
    }
}
