# Minecraft Server Deploy
This example updates the mod on a Minecraft server (managed by [AMP](https://cubecoders.com/AMP)) running it.
We use this to automatically update the [JCraft](https://github.com/JCraft-EoE/jcraft-eoe) Beta server.

For this to work, it's required Webhook runs as the amp user as the mod files must be owned by it and only it can
manage AMP instances using `ampinstmgr`.  
To achieve this, run `sudo systemctl edit webhook` and enter the following override:
```
[Service]
User=amp
Group=amp
ExecStart=
ExecStart=/usr/bin/webhook -hooks /etc/webhook/hooks.json -port 9000 -verbose
```
