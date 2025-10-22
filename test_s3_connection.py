#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
S3连接测试程序
用于验证S3配置是否正确
"""

import boto3
import sys
from botocore.exceptions import ClientError, NoCredentialsError, EndpointConnectionError
from botocore.config import Config

def test_s3_connection():
    """测试S3连接和认证"""
    
    # S3配置
    endpoint_url = "http://192.168.30.132:9010"
    access_key = "1tdvQZ5Er0CUSckmLs17"
    secret_key = "Eq5VQMgp9WCXb7NamK4yhcUGYdk6nw8esJZBf2v0"
    bucket_name = "dataagent"
    region = "us-east-1"
    
    print("=" * 60)
    print("S3连接测试程序")
    print("=" * 60)
    print(f"Endpoint: {endpoint_url}")
    print(f"Access Key: {access_key}")
    print(f"Secret Key: {secret_key[:8]}...")
    print(f"Bucket: {bucket_name}")
    print(f"Region: {region}")
    print("=" * 60)
    
    try:
        # 创建S3客户端
        print("1. 创建S3客户端...")
        s3_client = boto3.client(
            's3',
            endpoint_url=endpoint_url,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
            region_name=region,
            config=Config(
                signature_version='s3v4',
                s3={
                    'addressing_style': 'path'
                }
            )
        )
        print("[OK] S3客户端创建成功")
        
        # 测试连接
        print("\n2. 测试连接...")
        try:
            response = s3_client.list_buckets()
            print("[OK] 连接成功，可以列出存储桶")
            print(f"  找到 {len(response.get('Buckets', []))} 个存储桶")
            
            # 显示所有存储桶
            for bucket in response.get('Buckets', []):
                print(f"  - {bucket['Name']} (创建时间: {bucket['CreationDate']})")
                
        except ClientError as e:
            error_code = e.response['Error']['Code']
            error_message = e.response['Error']['Message']
            print(f"[ERROR] 连接失败: {error_code} - {error_message}")
            
            if error_code == 'InvalidAccessKeyId':
                print("  原因: Access Key ID 无效")
            elif error_code == 'SignatureDoesNotMatch':
                print("  原因: Secret Key 无效或签名不匹配")
            elif error_code == 'AccessDenied':
                print("  原因: 访问被拒绝，可能是权限不足")
            elif 'not signed up' in error_message.lower():
                print("  原因: 账户未注册或服务未启用")
            else:
                print(f"  原因: {error_message}")
            
            return False
            
        # 测试存储桶访问
        print(f"\n3. 测试存储桶 '{bucket_name}' 访问...")
        try:
            # 检查存储桶是否存在
            s3_client.head_bucket(Bucket=bucket_name)
            print(f"✓ 存储桶 '{bucket_name}' 存在且可访问")
            
            # 尝试列出存储桶中的对象
            response = s3_client.list_objects_v2(Bucket=bucket_name, MaxKeys=5)
            object_count = response.get('KeyCount', 0)
            print(f"✓ 存储桶中有 {object_count} 个对象")
            
            if object_count > 0:
                print("  前几个对象:")
                for obj in response.get('Contents', []):
                    print(f"  - {obj['Key']} (大小: {obj['Size']} bytes)")
            
        except ClientError as e:
            error_code = e.response['Error']['Code']
            if error_code == 'NoSuchBucket':
                print(f"✗ 存储桶 '{bucket_name}' 不存在")
                print("  尝试创建存储桶...")
                try:
                    s3_client.create_bucket(Bucket=bucket_name)
                    print(f"✓ 存储桶 '{bucket_name}' 创建成功")
                except ClientError as create_error:
                    print(f"✗ 创建存储桶失败: {create_error}")
                    return False
            else:
                print(f"✗ 访问存储桶失败: {error_code} - {e.response['Error']['Message']}")
                return False
        
        # 测试文件上传
        print(f"\n4. 测试文件上传...")
        test_content = "Hello, S3! This is a test file."
        test_key = "test-upload.txt"
        
        try:
            s3_client.put_object(
                Bucket=bucket_name,
                Key=test_key,
                Body=test_content.encode('utf-8'),
                ContentType='text/plain'
            )
            print(f"✓ 测试文件上传成功: {test_key}")
            
            # 验证文件是否存在
            response = s3_client.head_object(Bucket=bucket_name, Key=test_key)
            print(f"✓ 文件验证成功，大小: {response['ContentLength']} bytes")
            
            # 清理测试文件
            s3_client.delete_object(Bucket=bucket_name, Key=test_key)
            print("✓ 测试文件已清理")
            
        except ClientError as e:
            print(f"✗ 文件上传失败: {e.response['Error']['Code']} - {e.response['Error']['Message']}")
            return False
        
        print("\n" + "=" * 60)
        print("✓ 所有测试通过！S3配置正确")
        print("=" * 60)
        return True
        
    except EndpointConnectionError as e:
        print(f"✗ 无法连接到端点: {e}")
        print("  请检查:")
        print("  1. 端点URL是否正确")
        print("  2. 服务是否正在运行")
        print("  3. 网络连接是否正常")
        return False
        
    except NoCredentialsError:
        print("✗ 未找到AWS凭证")
        return False
        
    except Exception as e:
        print(f"✗ 未知错误: {e}")
        return False

def test_different_endpoints():
    """测试不同的端点格式"""
    print("\n" + "=" * 60)
    print("测试不同端点格式")
    print("=" * 60)
    
    endpoints = [
        "http://192.168.30.132:9010",
        "http://192.168.30.132:9010/",
        "https://192.168.30.132:9010",
    ]
    
    access_key = "1tdvQZ5Er0CUSckmLs17"
    secret_key = "Eq5VQMgp9WCXb7NamK4yhcUGYdk6nw8esJZBf2v0"
    
    for endpoint in endpoints:
        print(f"\n测试端点: {endpoint}")
        try:
            s3_client = boto3.client(
                's3',
                endpoint_url=endpoint,
                aws_access_key_id=access_key,
                aws_secret_access_key=secret_key,
                region_name='us-east-1'
            )
            
            # 尝试列出存储桶
            response = s3_client.list_buckets()
            print(f"✓ 成功: 找到 {len(response.get('Buckets', []))} 个存储桶")
            
        except Exception as e:
            print(f"✗ 失败: {e}")

if __name__ == "__main__":
    print("开始S3连接测试...")
    
    # 主要测试
    success = test_s3_connection()
    
    if not success:
        print("\n主要测试失败，尝试其他端点格式...")
        test_different_endpoints()
    
    print("\n测试完成")
    sys.exit(0 if success else 1)
