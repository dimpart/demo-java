# Decentralized Instant Message (Java Demo)

[![License](https://img.shields.io/github/license/dimpart/demo-java)](https://github.com/dimpart/demo-java/blob/master/LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/dimpart/demo-java/pulls)
[![Platform](https://img.shields.io/badge/Platform-Java%208-brightgreen.svg)](https://github.com/dimpart/demo-java/wiki)
[![Issues](https://img.shields.io/github/issues/dimpart/demo-java)](https://github.com/dimpart/demo-java/issues)
[![Repo Size](https://img.shields.io/github/repo-size/dimpart/demo-java)](https://github.com/dimpart/demo-java/archive/refs/heads/main.zip)
[![Tags](https://img.shields.io/github/tag/dimpart/demo-java)](https://github.com/dimpart/demo-java/tags)
[![Version](https://img.shields.io/maven-central/v/chat.dim/Client)](https://mvnrepository.com/artifact/chat.dim/Client)

[![Watchers](https://img.shields.io/github/watchers/dimpart/demo-java)](https://github.com/dimpart/demo-java/watchers)
[![Forks](https://img.shields.io/github/forks/dimpart/demo-java)](https://github.com/dimpart/demo-java/forks)
[![Stars](https://img.shields.io/github/stars/dimpart/demo-java)](https://github.com/dimpart/demo-java/stargazers)
[![Followers](https://img.shields.io/github/followers/dimpart)](https://github.com/orgs/dimpart/followers)

## Dependencies

* Latest Versions

| Name | Version | Description |
|------|---------|-------------|
| [Ming Ke Ming (名可名)](https://github.com/dimchat/mkm-java) | [![Version](https://img.shields.io/maven-central/v/chat.dim/MingKeMing)](https://mvnrepository.com/artifact/chat.dim/MingKeMing) | Decentralized User Identity Authentication |
| [Dao Ke Dao (道可道)](https://github.com/dimchat/dkd-java) | [![Version](https://img.shields.io/maven-central/v/chat.dim/DaoKeDao)](https://mvnrepository.com/artifact/chat.dim/DaoKeDao) | Universal Message Module |
| [DIMP (去中心化通讯协议)](https://github.com/dimchat/core-java) | [![Version](https://img.shields.io/maven-central/v/chat.dim/DIMP)](https://mvnrepository.com/artifact/chat.dim/DIMP) | Decentralized Instant Messaging Protocol |
| [DIM SDK](https://github.com/dimchat/sdk-java) | [![Version](https://img.shields.io/maven-central/v/chat.dim/SDK)](https://mvnrepository.com/artifact/chat.dim/SDK) | Software Development Kit |
| [DIM Plugins](https://github.com/dimchat/plugins-java) | [![Version](https://img.shields.io/maven-central/v/chat.dim/Plugins)](https://mvnrepository.com/artifact/chat.dim/Plugins) | Cryptography & Account Plugins |
| [LNC](https://github.com/dimpart/demo-java) | [![Version](https://img.shields.io/maven-central/v/chat.dim/LNC)](https://mvnrepository.com/artifact/chat.dim/LNC) | Log, Notification & Cache |
| [StarGate](https://github.com/dimpart/demo-java) | [![Version](https://img.shields.io/maven-central/v/chat.dim/StarGate)](https://mvnrepository.com/artifact/chat.dim/StarGate) | Network Connection Module |

* build.gradle

```javascript
allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

dependencies {
    implementation group: 'chat.dim', name: 'Client', version: '0.5.2'
}
```

* pom.xml

```xml
<dependencies>

    <!-- https://mvnrepository.com/artifact/chat.dim/Client -->
    <dependency>
        <groupId>chat.dim</groupId>
        <artifactId>Client</artifactId>
        <version>0.5.2</version>
        <type>pom</type>
    </dependency>

</dependencies>
```

Copyright &copy; 2023 Albert Moky
[![Followers](https://img.shields.io/github/followers/moky)](https://github.com/moky?tab=followers)
