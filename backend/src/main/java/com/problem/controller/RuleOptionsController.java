package com.problem.controller;

import com.problem.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rule-options")
public class RuleOptionsController {

    @GetMapping
    public Result<?> getRuleOptions() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("problemCategories", List.of(
            "\u901a\u8baf/\u7f51\u7edc\u95ee\u9898",
            "\u8bbe\u5907\u786c\u4ef6\u6545\u969c",
            "\u914d\u7f6e/\u53c2\u6570\u95ee\u9898",
            "\u7b56\u7565/\u8054\u52a8/\u5b9a\u65f6\u95ee\u9898",
            "\u8f6f\u4ef6\u5e73\u53f0/\u670d\u52a1\u95ee\u9898",
            "\u6570\u636e/\u7edf\u8ba1\u95ee\u9898",
            "\u65bd\u5de5/\u7ebf\u8def/\u4f9b\u7535\u95ee\u9898",
            "\u64cd\u4f5c\u4f7f\u7528\u95ee\u9898",
            "\u7b2c\u4e09\u65b9\u8bbe\u5907/\u534f\u8bae\u63a5\u5165\u95ee\u9898",
            "\u9700\u6c42\u53d8\u66f4",
            "\u5f85\u786e\u8ba4\u95ee\u9898"
        ));
        options.put("causeCategories", List.of(
            "\u5e73\u53f0\u670d\u52a1\u5f02\u5e38",
            "\u6570\u636e\u5e93/\u6570\u636e\u5904\u7406\u5f02\u5e38",
            "\u7f51\u7edc\u4e0d\u901a/\u4e0d\u7a33\u5b9a",
            "\u7f51\u5173\u79bb\u7ebf",
            "\u8bbe\u5907\u6389\u7ebf/\u672a\u5165\u7f51",
            "\u5730\u5740/\u53c2\u6570\u914d\u7f6e\u9519\u8bef",
            "\u533a\u57df/\u8bbe\u5907\u7ed1\u5b9a\u9519\u8bef",
            "\u8ba2\u9605/\u573a\u666f\u914d\u7f6e\u7f3a\u5931",
            "\u7b56\u7565\u914d\u7f6e\u9519\u8bef/\u51b2\u7a81",
            "\u7ebf\u8def\u63a5\u9519/\u65ad\u7ebf/\u77ed\u8def",
            "\u4f9b\u7535\u5f02\u5e38",
            "\u786c\u4ef6\u635f\u574f",
            "\u7b2c\u4e09\u65b9\u534f\u8bae/\u9002\u914d\u5f02\u5e38",
            "\u73b0\u573a\u73af\u5883/\u5b89\u88c5\u5f71\u54cd",
            "\u5ba2\u6237\u64cd\u4f5c\u4e0d\u5f53/\u6743\u9650\u95ee\u9898",
            "\u9700\u6c42\u53d8\u66f4",
            "\u539f\u56e0\u5f85\u786e\u8ba4"
        ));
        options.put("sourceChannels", List.of("\u5ba2\u6237\u5fae\u4fe1\u7fa4", "\u7535\u8bdd", "\u90ae\u4ef6", "\u73b0\u573a\u5de1\u68c0", "\u7cfb\u7edf\u544a\u8b66", "Excel/CSV \u5bfc\u5165", "\u5185\u90e8\u6392\u67e5", "\u624b\u52a8\u5f55\u5165"));
        options.put("priorities", List.of("\u9ad8", "\u4e2d", "\u4f4e"));
        options.put("statuses", List.of(
            Map.of("value", "OPEN", "label", "\u5f85\u5904\u7406"),
            Map.of("value", "IN_PROGRESS", "label", "\u5904\u7406\u4e2d"),
            Map.of("value", "PENDING_CONFIRM", "label", "\u5f85\u786e\u8ba4"),
            Map.of("value", "SUSPENDED", "label", "\u5df2\u6302\u8d77"),
            Map.of("value", "CLOSED", "label", "\u5df2\u5173\u95ed")
        ));
        options.put("systemTypes", List.of("\u7167\u660e", "\u7a7a\u8c03", "\u5916\u906e\u9633/\u7a97\u5e18", "\u7f51\u5173", "\u4f20\u611f\u5668", "\u8f6f\u4ef6\u5e73\u53f0", "\u7b2c\u4e09\u65b9\u8bbe\u5907", "\u672a\u786e\u8ba4"));
        options.put("recommendedTags", List.of("GW", "CU", "SCU", "OCSR", "MCBOX", "Socket", "485", "\u5b9a\u65f6\u4efb\u52a1", "\u7f51\u5173\u79bb\u7ebf", "\u80fd\u8017\u5f02\u5e38", "\u8ba2\u9605", "\u573a\u666f"));
        return Result.success(options);
    }
}
