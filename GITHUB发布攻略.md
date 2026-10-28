# GitHub 注册与发布攻略（新手版）

> 目标：把「声记」源码和 APK 发布到 GitHub。
> 全程两种方式：**图形界面（GitHub Desktop，推荐新手）** 和 **命令行（git）**。

---

## 〇、发布前先做一次自检（很重要）

发之前，确认仓库里**没有**这些：

- [ ] 任何 `sk-` 开头的 API Key（代码、文档、APK 里都不能有）
- [ ] 你的个人路径（如 `D:\我的项目\...` 或 `/home/你的用户名/...`）、姓名、手机号
- [ ] 模型大文件（`assets/*.onnx`）、`libs/`、`build/`、`*.apk`（`.gitignore` 已排除，确认生效）
- [ ] 来源不明的图片（`art/` 已排除）
- [ ] 标题/描述里没有「官方」「合作」「推荐」等字样

---

## 一、注册 GitHub

1. 打开 <https://github.com> → 点右上角 **Sign up**。
2. 依次填：
   - **Email**：常用邮箱；
   - **Password**：至少 8 位，含字母和数字；
   - **Username**：英文/数字/连字符，比如 `yourname`（会成为你的主页地址）；
   - 完成人机验证。
3. 去邮箱收验证邮件，点里面的 **Verify email address**。
4. （可选但建议）开启两步验证：右上角头像 → Settings → Password and authentication → Enable 2FA。

> 国内访问 GitHub 有时慢，属正常；换网络或稍后再试。

---

## 二、把代码传到 GitHub

### 方式 A：GitHub Desktop（图形界面，零命令，推荐）

1. 下载安装 **GitHub Desktop**：<https://desktop.github.com>
2. 打开 → **Sign in to GitHub.com** → 浏览器授权登录。
3. 菜单 **File → Add local repository** → 选择你的项目文件夹（含 `README.md` 的那个）。
   - 如果提示「not a Git repository」，点 **create a repository**，Name 填 `voicemin`，点 Create。
4. 左下角填 **Summary**（如 `first commit`）→ 点 **Commit to main**。
5. 点顶部 **Publish repository**：
   - Name：`voicemin`；
   - 取消勾选 **Keep this code private**（要公开就取消）；
   - 点 **Publish repository**。

完成，浏览器打开 `https://github.com/你的用户名/voicemin` 就能看到。

### 方式 B：命令行（git）

前置：装好 Git for Windows（<https://git-scm.com/download/win>，一路下一步）。

```powershell
cd "你的项目文件夹"

git init
git config user.name  "你的名字"
git config user.email "你的邮箱"

git add .
git commit -m "first commit"
git branch -M main

# 先在 GitHub 网页上 New repository（名字 voicemin，不要勾 Add README）
git remote add origin https://github.com/你的用户名/voicemin.git
git push -u origin main
```

首次 `push` 会要求登录：按提示在浏览器授权，或用 **Personal Access Token**（Settings → Developer settings → Personal access tokens → Generate new token，勾 `repo`）当密码。

---

## 三、发布 APK（Release）

GitHub 的「Release」适合放编译好的 APK（单文件上限 2GB）。

1. 打开你的仓库 → 右侧 **Releases** → **Create a new release**。
2. 填：
   - **Choose a tag**：输入 `v0.8.2` → 点 **Create new tag**；
   - **Release title**：`声记 v0.8.2`；
   - **Describe**：写更新内容 + 安装说明 + SHA256；
3. **Attach binaries**：把 `build\app-release.apk` 改名为 `voicemin-v0.8.2.apk` 后拖进去；
4. 点 **Publish release**。

> 建议同时把 `安装与使用说明.md`、`THIRD_PARTY_NOTICES.md` 一起放进去，或确保仓库里有。

### 附：算 SHA256（Windows）

```powershell
Get-FileHash .\voicemin-v0.8.2.apk -Algorithm SHA256
```

把结果贴到 Release 说明里，方便别人校验。

---

## 四、常见问题

| 问题 | 解决 |
|---|---|
| `git push` 让输密码，输了不对 | GitHub 不再支持密码推送；用浏览器授权、GitHub Desktop，或 Personal Access Token |
| 提示文件太大（>100MB） | 不要把模型/APK 提交进 git；用 Release 放 APK，模型用 `scripts/setup.ps1` 下载 |
| 不小心提交了 Key | 立刻去 DeepSeek 平台**删除该 Key 并重建**；然后 `git rm --cached 文件` 再提交；若已推送，用 BFG 或删库重来 |
| 国内访问慢 | 换网络；或用 Gitee 做镜像（见下） |
| 想改仓库名/描述 | 仓库 → Settings → 顶部重命名；Description 在 About 里改 |

### Gitee 镜像（可选，国内快）

1. <https://gitee.com> 注册；
2. 新建仓库 → 选择「从 GitHub 导入」；
3. 填 GitHub 仓库地址，导入即可。

---

## 五、仓库里应该有什么（对照检查）

```
README.md                 # 项目介绍、构建步骤
LICENSE                   # MIT（改一下版权人）
THIRD_PARTY_NOTICES.md    # 第三方组件与模型许可证
.gitignore                # 排除模型/APK/构建产物
app/                      # 源码
scripts/                  # setup.ps1 / build.ps1 / assemble.py
docs/                     # 说明、许可证原文
安装与使用说明.md
```

> 模型（`assets/*.onnx`）、`libs/`、`build/`、`*.apk` 都不进 git；别人克隆后先跑 `scripts/setup.ps1` 再 `scripts/build.ps1`。

---

## 六、发布后

- 把 Release 链接发到需要的地方（记得先看平台对外链的规定）；
- 有人反馈问题时，优先看：是否给了麦克风权限、是否放行后台、是否填了自己的 Key；
- 后续更新：改代码 → 重新 `build.ps1` → 新建一个 Release（版本号 +1）。

---

## 七、一句话流程

**注册 → GitHub Desktop 发布仓库 → Release 上传 APK → 贴 SHA256 → 完事。**
