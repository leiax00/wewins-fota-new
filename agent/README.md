# Agent使用方法

## 概述
当前目录包含了一个插件市场和若干插件，可以进行快速引入自己的仓库

```shell
# 安装插件市场
/plugin marketplace add ./

# 安装插件
/plugin install fota-code-bundles@fota-coding
## 推荐选择只为当前仓库安装

# 卸载插件
/plugin uninstall fota-code-bundles@fota-coding

# 卸载插件市场
/plugin marketplace remove we-coding

# 规则安装 （必须手动安装）
rm -rf ~/.claude/rules
mkdir ~/.claude/rules
cp -r ./agent/rules/* ~/.claude/rules/

# 智能体安装
mkdir ~/.claude/agents
cp -r ./agent/agents/* ~/.claude/agents/
```