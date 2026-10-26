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
package cn.jfspeed.common.data.util.page;

import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.core.util.page.PageReq;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Collections;
import java.util.List;

/**
 * 分页工具：MyBatis Plus 分页结果与统一分页响应（{@link PageRes}）之间的转换。
 *
 * @author jffast
 * @date 2024/1/24 18:05
 */
public class PageUtil {

    /**
     * 将 MyBatis Plus 的分页结果转换为统一分页响应。
     *
     * @param p   MyBatis Plus 的分页结果
     * @param <P> 记录类型
     * @return 统一分页响应（无数据时返回空列表）
     */
    public static <P> PageRes<P> resultPageMp(IPage<P> p){
        // 1. 非空校验
        List<P> records = p.getRecords();
        if(records == null || records.isEmpty()){
            // 无数据，返回空结果
            return new PageRes<>(p.getTotal(), p.getPages(), Collections.emptyList());
        }
        // 3. 封装返回
        return new PageRes<>(p.getTotal(), p.getPages(), records);
    }

    /**
     * 以实体非空字段作为等值条件构建查询包装器。
     *
     * @param entity 条件实体
     * @param <T>    实体类型
     * @return Lambda 查询包装器
     */
    public static <T> LambdaQueryWrapper<T> getQueryWrapper(T entity) {
        return new LambdaQueryWrapper<T>(entity);
    }

    /**
     * 将统一分页请求转换为 MyBatis Plus 分页对象。
     *
     * @param pageReq 统一分页请求（pageNo / pageSize）
     * @param <T>     记录类型
     * @return MyBatis Plus 分页对象
     */
    public static <T> com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> getPageMp(PageReq pageReq) {
        // 委托 PageReq.getPage()：pageNo / pageSize 为空时走默认值，避免直接拆箱 Integer 触发 NPE
        return pageReq.getPage();
    }
}
