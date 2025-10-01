# 查看所有Docker网络的配置信息
Write-Host "=== Docker网络配置信息 ===" -ForegroundColor Green

# 获取所有网络ID
$networkIds = docker network ls -q

foreach ($networkId in $networkIds) {
    Write-Host "`n--- 网络ID: $networkId ---" -ForegroundColor Yellow
    
    # 获取网络详细信息
    $networkInfo = docker network inspect $networkId | ConvertFrom-Json
    
    Write-Host "网络名称: $($networkInfo.Name)" -ForegroundColor Cyan
    Write-Host "驱动类型: $($networkInfo.Driver)" -ForegroundColor Cyan
    
    # 检查是否有IPAM配置
    if ($networkInfo.IPAM -and $networkInfo.IPAM.Config) {
        foreach ($config in $networkInfo.IPAM.Config) {
            if ($config.Subnet) {
                Write-Host "子网: $($config.Subnet)" -ForegroundColor White
            }
            if ($config.Gateway) {
                Write-Host "网关: $($config.Gateway)" -ForegroundColor White
            }
        }
    } else {
        Write-Host "子网: 默认配置" -ForegroundColor Gray
    }
    
    # 显示连接的容器
    if ($networkInfo.Containers) {
        Write-Host "连接的容器:" -ForegroundColor Magenta
        foreach ($container in $networkInfo.Containers.PSObject.Properties) {
            $containerInfo = $container.Value
            Write-Host "  - $($containerInfo.Name) (IP: $($containerInfo.IPv4Address))" -ForegroundColor White
        }
    } else {
        Write-Host "连接的容器: 无" -ForegroundColor Gray
    }
}

Write-Host "`n=== 网络配置检查完成 ===" -ForegroundColor Green
