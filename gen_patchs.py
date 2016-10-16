#!/usr/bin/env python
# -*- coding:utf-8 -*-

import sys, os, shutil, json
from hashlib import md5

APK_DIR = ''
APP_NAME = ''
SEPARATOR = ''
EXT = ''
PATCH_EXT = ''
PATCH_FOLDER = ''
# patch name: app name-old version-new version-channel-new version md5.patch

def load_config():
    global APK_DIR
    global SEPARATOR
    global APP_NAME
    global PATCH_EXT
    global PATCH_FOLDER
    global EXT
    config = json.loads(open('config.json').read())
    APK_DIR = config['apk_dir']
    APP_NAME = config['app_name']
    SEPARATOR = config['separator']
    EXT = config['ext']
    PATCH_EXT = config['patch_ext']
    PATCH_FOLDER = config['patch_folder']
    # print APK_DIR, APP_NAME, SEPARATOR, EXT, PATCH_EXT, PATCH_FOLDER

def exe_cmd(cmd):
    ret = os.popen(cmd)
    text = ret.read()
    ret.close()
    return text

def check_version_dir(version):
    global APK_DIR
    path = os.path.join(APK_DIR, version)
    if not os.path.exists(path):
        print path, 'does not exists'
        return False
    if not os.path.isdir(path):
        print path, 'is not a dir'
        return False
    return True

def get_md5(path):
    """获取文件的MD5
    Args:
    path：文件路径
    """
    m = md5()
    file = open(path, 'rb')
    m.update(file.read())
    file.close
    return m.hexdigest()

def get_all_versions():
    """获取所有版本号
    """
    global APK_DIR
    versions = []
    for temp in os.listdir(APK_DIR):
        if not os.path.isdir(os.path.join(APK_DIR, temp)):continue
        try:
            version = float(temp)
            versions.append(temp)
        except:
            pass
    versions.sort(reverse = True)
    return versions

def check_patch(old_version_path, new_version_path, patch_path, output_log):
    """
    检查patch生成的是否正确。将patch和旧版本合并，然后于新版本对比md5
    Args:
    old_version_path: 旧版本路径
    new_version_path: 新版本号路径
    patch_path: patch路径
    """
    global APK_DIR
    temp_new_version_path = os.path.join(APK_DIR, 'temp_cache')
    cmd = './bspatch ' + old_version_path + ' ' + temp_new_version_path + ' ' + patch_path;
    exe_cmd(cmd)
    if not os.path.exists(temp_new_version_path):
        if output_log: print 'check patch failed. cannot get new file from patch', old_version_path, new_version_path, patch_path
        return False
    new_version_md5 = get_md5(new_version_path)
    temp_new_version_md5 = get_md5(temp_new_version_path)
    os.remove(temp_new_version_path)
    if new_version_md5 != temp_new_version_md5:
        if output_log: print 'check patch failed. check md5 failed'
        return False
    return True

def gen_patchs_from_to(old_version, new_version):
    """生成从旧版本到新版本的各个渠道的patch
    例如：old_version=3.0，new_version=4.0，即生成3.0->4.0的patch
    Args:
    old_version: 旧版本号
    new_version: 新版本号
    output_log: 是否输出log
    """
    global APK_DIR
    global SEPARATOR
    global APP_NAME
    global PATCH_EXT
    global PATCH_FOLDER
    global EXT
    # print 'gen_patchs_from_to', old_version, new_version
    if not check_version_dir(old_version) or not check_version_dir(new_version):
        return
    new_version_dir = os.path.join(APK_DIR, new_version)
    files = os.listdir(new_version_dir)
    # 遍历新版下的所有文件，根据文件名规则拼接出旧版的路径
    for file_name in files:
        new_version_path = os.path.join(new_version_dir, file_name)
        file_name, ext = os.path.splitext(file_name)
        if ext != EXT: continue
        parts = file_name.split(SEPARATOR);
        if len(parts) < 3:
            print 'bad file name', file_name
            continue
        channel = parts[2]
        
        old_version_name = APP_NAME + SEPARATOR + old_version + SEPARATOR + channel + EXT;
        old_version_path = os.path.join(APK_DIR, old_version, old_version_name)
        if not os.path.exists(old_version_path):
            print 'old version file does not exists. ', old_version_path
            continue
        
        new_version_md5 = get_md5(new_version_path)
        patch_name = APP_NAME + SEPARATOR + old_version + SEPARATOR + new_version + SEPARATOR + channel + SEPARATOR + new_version_md5 + PATCH_EXT
        patch_folder = os.path.join(new_version_dir, PATCH_FOLDER)
        if not os.path.exists(patch_folder): os.mkdir(patch_folder)
        patch_path = os.path.join(patch_folder, patch_name)
        # 如果已经存在patch文件，就直接check。失败了就重新生成patch，否则直接跳过
        if os.path.exists(patch_path) and check_patch(old_version_path, new_version_path, patch_path, False):
            print 'gen patch success. patch already exists.', patch_path
            continue

        cmd = './bsdiff ' + old_version_path + ' ' + new_version_path + ' ' + patch_path
        exe_cmd(cmd)
        if os.path.exists(patch_path):
            if check_patch(old_version_path, new_version_path, patch_path, True):
                print 'gen patch success ', patch_path
            else:
                print 'delete patch ', patch_path
                os.remove(patch_path)
        else:
            print 'gen patch failed. ', old_version_path, new_version_path
        
def gen_patchs_for(version, count):
    """为特定的版本号生成前count个版本的patch
    例如：version=4.0，count=2，即生成3.0->4.0，2.0->4.0的patch
    Args:
    version: 版本号
    count: 版本数量
    """
    if not check_version_dir(version): return
    
    versions = get_all_versions()
    if len(versions) == 0: return

    idx = versions.index(version)
    if idx < 0: return
    idx = idx + 1
    versions = versions[idx : count + idx]
    for v in versions:
        gen_patchs_from_to(v, version)

def gen_patchs_for_all():
    """生成所有旧版本到新版本的patch
    """
    versions = get_all_versions()
    if len(versions) == 0: return
    length = len(versions) - 1
    for i in range(length):
        gen_patchs_for(versions[i], length - i)

def output_help():
    print '假设当前有1.0，2.0，3.0, 4.0这4个版本'
    print './gen_patchs -a 生成所有版本的patch，1.0->2.0；1.0->3.0，1.0->4.0，2.0->3.0, 2.0->4.0, 3.0->4.0'
    print './gen_patchs 1.0 2.0 生成从1.0到2.0的patch，1.0->2.0'
    print './gen_patchs -c 2 4.0 生成所有小于3.0的版本到3.0的patch，1.0->3.0，2.0->3.0'

def main(argv):
    load_config()
    argv_length = len(argv)
    if argv_length == 1:
        print 'need more args'
    elif argv_length == 2:
        if argv[1] == '-a':
            gen_patchs_for_all()
        elif argv[1] == '-h':
            output_help()
    elif argv_length == 3:
        gen_patchs_from_to(argv[1], argv[2])
    elif argv_length == 4 and argv[1] == '-c':
        gen_patchs_for(argv[3], int(argv[2]))
    else:
        output_help()

if __name__ == '__main__':
    main(sys.argv)
