# Changelog

本文档记录项目所有重要变更，格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)。

## [Unreleased]

### 0.0.2
- 支持通过map或者其他集合类/字符串数组创建Bom
- 新增 createWithEmptyValue 的 String... 变参重载
- 集合字段缺少 @BomMapping genericType 时自动推断泛型参数
- map 方法支持实现作为 models 参数