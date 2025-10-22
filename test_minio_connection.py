#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MinIO连接测试程序
"""

import boto3
import sys
from botocore.exceptions import ClientError, NoCredentialsError, EndpointConnectionError
from botocore.config import Config

def test_minio_connection():
    """测试MinIO连接和认证"""
    
    # MinIO配置
    endpoint_url = "http://localhost:9000"  # 本地MinIO
    access_key = "minioadmin"
    secret_key = "minioadmin123"
    bucket_name = "dataagent"
    region = "us-east-1"
    
    print("=" * 60)
    print("MinIO连接测试程序")
    print("=" * 60)
    print(f"Endpoint: {endpoint_url}")
    print(f"Access Key: {access_key}")
    print(f"Secret Key: {secret_key}")
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
            return False
            
        # 测试存储桶访问
        print(f"\n3. 测试存储桶 '{bucket_name}' 访问...")
        try:
            # 检查存储桶是否存在
            s3_client.head_bucket(Bucket=bucket_name)
            print(f"[OK] 存储桶 '{bucket_name}' 存在且可访问")
            
        except ClientError as e:
            error_code = e.response['Error']['Code']
            if error_code == 'NoSuchBucket':
                print(f"[WARNING] 存储桶 '{bucket_name}' 不存在")
                print("  尝试创建存储桶...")
                try:
                    s3_client.create_bucket(Bucket=bucket_name)
                    print(f"[OK] 存储桶 '{bucket_name}' 创建成功")
                except ClientError as create_error:
                    print(f"[ERROR] 创建存储桶失败: {create_error}")
                    return False
            else:
                print(f"[ERROR] 访问存储桶失败: {error_code} - {e.response['Error']['Message']}")
                return False
        
        # 测试文件上传
        print(f"\n4. 测试文件上传...")
        test_content = "Hello, MinIO! This is a test file."
        test_key = "test-upload.txt"
        
        try:
            s3_client.put_object(
                Bucket=bucket_name,
                Key=test_key,
                Body=test_content.encode('utf-8'),
                ContentType='text/plain'
            )
            print(f"[OK] 测试文件上传成功: {test_key}")
            
            # 验证文件是否存在
            response = s3_client.head_object(Bucket=bucket_name, Key=test_key)
            print(f"[OK] 文件验证成功，大小: {response['ContentLength']} bytes")
            
            # 测试文件下载
            response = s3_client.get_object(Bucket=bucket_name, Key=test_key)
            downloaded_content = response['Body'].read().decode('utf-8')
            if downloaded_content == test_content:
                print("[OK] 文件下载验证成功")
            else:
                print("[ERROR] 文件下载验证失败")
                return False
            
            # 清理测试文件
            s3_client.delete_object(Bucket=bucket_name, Key=test_key)
            print("[OK] 测试文件已清理")
            
        except ClientError as e:
            print(f"[ERROR] 文件操作失败: {e.response['Error']['Code']} - {e.response['Error']['Message']}")
            return False
        
        print("\n" + "=" * 60)
        print("[SUCCESS] 所有测试通过！MinIO配置正确")
        print("=" * 60)
        return True
        
    except EndpointConnectionError as e:
        print(f"[ERROR] 无法连接到端点: {e}")
        print("  请检查:")
        print("  1. MinIO服务是否正在运行")
        print("  2. 端口9000是否可访问")
        print("  3. 网络连接是否正常")
        return False
        
    except NoCredentialsError:
        print("[ERROR] 未找到AWS凭证")
        return False
        
    except Exception as e:
        print(f"[ERROR] 未知错误: {e}")
        return False

if __name__ == "__main__":
    print("开始MinIO连接测试...")
    
    # 主要测试
    success = test_minio_connection()
    
    print("\n测试完成")
    sys.exit(0 if success else 1)
