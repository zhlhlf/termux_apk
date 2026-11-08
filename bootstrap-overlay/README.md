# bootstrap overlay

Files copied over the official Termux `apt-android-7` aarch64 bootstrap before it is
repackaged and attached to a release:

| file | change |
| --- | --- |
| `etc/apt/sources.list` | APT sources point at the BFSU mirror (`https://mirrors.bfsu.edu.cn/termux/apt/termux-main`); official repo kept as a comment |
| `etc/termux-login.sh` | prints `by zhlhlf` on login |
| `etc/motd` | ASCII "Termux" logo, printed in orange (`\033[38;5;208m`) |

Repack an upstream bootstrap:

```sh
./apply.sh bootstrap-aarch64.zip bootstrap-aarch64-bfsu.zip
```

Upstream source used by the current build: `bootstrap-2026.02.12-r1` +
`apt.android-7` (sha256 `ea2aeba8…f4f3`). The repacked zip is published as the
`bootstrap-2026.02.12-bfsu-r2` release and downloaded by `app/build.gradle`.
