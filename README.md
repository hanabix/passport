[![Codacy Badge](https://api.codacy.com/project/badge/Grade/31c3f709c7c646ea80b7c4fcd130507b)](https://www.codacy.com/app/zhonglunfu/passport?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=zhongl/passport&amp;utm_campaign=Badge_Grade)
[![Build Status](https://travis-ci.org/zhongl/passport.svg?branch=master)](https://travis-ci.org/zhongl/passport)
[![Docker Version](https://img.shields.io/github/tag/zhongl/passport.svg)](https://hub.docker.com/r/zhongl/passport)
[![Coverage Status](https://coveralls.io/repos/github/zhongl/passport/badge.svg?branch=master)](https://coveralls.io/github/zhongl/passport?branch=master)


Passport 是一个超轻量级统一认证网关, 面向使用 [钉钉](https://www.dingtalk.com) 或是 [企业微信](https://work.weixin.qq.com/) 的创业团队提供手机扫码登录访问内部服务.

# 部署

## passport.conf

### 钉钉

```conf
include "dingtalk.conf"

cookie {
    domain = ".company.internal.domain"
    secret = "JWT签名密钥"
}

dingtalk {
    micro {
        appkey = "微应用的appkey"
        appsec = "微应用的appsecret"
    }
    
    mobile {
        appid = "移动接入应用的appid"
        appsec = "移动接入应用的appSecret"
    }
}
```

> 1. 参见[开发企业内部应用](https://open-doc.dingtalk.com/microapp/bgb96b/aw3h75), 创建**微应用**;
> 1. 参见[扫码登录第三方Web网站](https://open-doc.dingtalk.com/microapp/serverapi2/kymkv6), 创建**移动接入应用**.

### 企业微信

```conf
include "wechat.conf"

cookie {
    domain = ".company.internal.domain"
    secret = "JWT签名密钥"
}

wechat {
  corp = "企业corpid"
  secret = "企业corpsecret"
  agent = "应用的agentid"
}
```

> 参见[企业内部开发](https://work.weixin.qq.com/api/doc#90000/90003/90487), 创建**应用**.

## 运行

```sh
docker run -d -v $(pwd)/app.conf:/app.conf -e JAVA_OPTS=-Dconfig.file=/app.conf zhongl/passport
```

## Echo调试

若需要在真正部署之前进行调试验证, 可在运行时指定`-e`:

```sh
docker run --rm -v $(pwd)/app.conf:/app.conf -e JAVA_OPTS=-Dconfig.file=/app.conf zhongl/passport -e
```

开启**Echo**模式, 即扫码登录后 Passport 不转发请求, 而是显示当前用户信息.

> `--help` 查看更多帮助

# 应用集成

> TODO

## References

- https://open-doc.dingtalk.com/microapp/debug/ucof2g