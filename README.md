[![CI](https://github.com/hanabix/passport/actions/workflows/ci.yml/badge.svg)](https://github.com/hanabix/passport/actions/workflows/ci.yml) [![Release](https://github.com/hanabix/passport/actions/workflows/release.yml/badge.svg)](https://github.com/hanabix/passport/actions/workflows/release.yml) [![Docker Latest](https://img.shields.io/docker/v/zhongl/passport/latest)](https://hub.docker.com/r/zhongl/passport) [![Coveralls github](https://img.shields.io/coveralls/github/hanabix/passport.svg)](https://coveralls.io/github/hanabix/passport?branch=master) [![Codacy Badge](https://app.codacy.com/project/badge/Grade/bc074d003a31425189769e4c97781b0a)](https://www.codacy.com/gh/hanabix/passport/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=hanabix/passport&amp;utm_campaign=Badge_Grade)


**Passport** 是一个超轻量级统一认证网关, 面向使用 [钉钉](https://www.dingtalk.com) 或是 [企业微信](https://work.weixin.qq.com/) 的创业团队提供手机扫码登录访问内部服务.

![overview diagram](http://www.plantuml.com/plantuml/proxy?src=https://raw.githubusercontent.com/zhongl/passport/master/.plantuml/deploy.txt)


![seq diagram](http://www.plantuml.com/plantuml/proxy?src=https://raw.githubusercontent.com/zhongl/passport/master/.plantuml/seq.txt)


## 跑起来

```sh
curl -LkO https://github.com/zhongl/passport/raw/master/docker-compose.yml 
curl -LkO https://github.com/zhongl/passport/raw/master/app.conf
DOMAIN=foo.bar docker-compose up -d
curl -k -v https://localhost -H 'Host: www.foo.bar' -H 'Cookie: jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJwYXNzcG9ydCIsIm5hbWUiOiJ6aG9uZ2wiLCJleHAiOjE4NjYxNzI3MjV9.FomLr4SgRvHuI6iUnVZc2-Q9YQbNrh4eDWGbM09xoC8'
```

- 细节请详见 [docker-compose.yml](https://github.com/zhongl/passport/blob/master/docker-compose.yml) . 


## 配置 

### 钉钉

```conf
// app.conf
include "dingtalk.conf"

cookie {
    domain = ".company.internal.domain"
    secret = "JWT签名密钥"
}

dingtalk {
    micro {
        appkey = "微应用的appkey"
        secret = "微应用的appsecret"
    }
    
    mobile {
        appid = "移动接入应用的appid"
        secret = "移动接入应用的appSecret"
    }

    authorization.redirect = "https://your.company.domain/authorized"
}
```

> 1. 参见[开发企业内部应用](https://open-doc.dingtalk.com/microapp/bgb96b/aw3h75), 创建**微应用**;
> 1. 参见[扫码登录第三方Web网站](https://open-doc.dingtalk.com/microapp/serverapi2/kymkv6), 创建**移动接入应用**.

### 企业微信

```conf
// app.conf
include "wechat.conf"

cookie {
    domain = ".company.internal.domain"
    secret = "JWT签名密钥"
}

wechat {
    corp = "企业corpid"
    secret = "企业corpsecret"
    agent = "应用的agentid"

    authorization.redirect = "https://your.company.domain/authorized"
}
```

> 参见[企业内部开发](https://work.weixin.qq.com/api/doc#90000/90003/90487), 创建**应用**.

## Echo调试

若需要在真正部署之前进行调试验证, 可在运行时指定`-e`:

```sh
docker run --rm -it zhongl/passport:latest -e
```

开启**Echo**模式, 显示请求文本.

> `docker run --rm zhongl/passport:latest --help` 查看更多帮助

## 应用集成

扫码登录后, Passport 会产生一个加签过的 [JWT](https://jwt.io) Token 作为 Cookie, 其中包含当前用户的认证信息(钉钉和微信略有差异). 此 Cookie 也会在后续的请求中透传到合法目标服务器, 做进一步授权处理.

> 认证信息参见[Platforms.scala](https://github.com/zhongl/passport/blob/master/src/main/scala/zhongl/passport/Platforms.scala)

## References

- https://open-doc.dingtalk.com/microapp/debug/ucof2g
