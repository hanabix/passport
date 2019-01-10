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
docker run -v $(pwd)/app.conf:/app.conf -e JAVA_OPTS=-Dconfig.file=/app.conf zhongl/passport:0.0.1
```

# 应用集成

> TODO

## References

- https://open-doc.dingtalk.com/microapp/debug/ucof2g