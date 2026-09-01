# 第三方数据说明

## rime-pinyin-simp

`app/src/main/assets/pinyin_rime.tsv` 由
[rime-pinyin-simp](https://github.com/rime/rime-pinyin-simp) 的
`pinyin_simp.dict.yaml` 转换生成。该词库源自 Android 开源项目 PinyinIME，按
Apache License 2.0 使用。完整许可文本见
`third_party_licenses/rime-pinyin-simp-LICENSE.txt`。

## hanzi_chaizi

`app/src/main/assets/assembly_full.tsv` 由
[hanzi_chaizi](https://github.com/howl-anderson/hanzi_chaizi) 的简体拆字数据
`raw_data/chaizi-jt.txt` 与上述拼音数据组合生成。`hanzi_chaizi` 程序按
Apache License 2.0 发布；其 README 说明原始拆字数据来自开放词典网，并按
[CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) 使用。依照该许可在此注明来源。
项目许可文本见 `third_party_licenses/hanzi-chaizi-LICENSE.txt`。

生成过程只做格式转换、读音映射、去重与索引键生成，可使用
`tools/generate_dictionaries.ps1` 重现。

## librime 及原生依赖

Android 原生解码器使用 [librime 1.16.1](https://github.com/rime/librime)，
按 BSD 3-Clause License 使用。静态链接的依赖包括 Boost（Boost Software
License 1.0）、LevelDB（BSD 3-Clause）、marisa-trie（BSD 2-Clause）、
OpenCC（Apache License 2.0）和 yaml-cpp（MIT）。相应完整许可文本见
`third_party_licenses`。项目没有复制或链接 Trime 的 GPL 界面实现。
