$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'

echo "import os

scripts_dir = os.path.dirname(os.path.abspath(__file__))
dst_list = os.listdir(scripts_dir)
dst_list.sort()

for dst in dst_list:
    dst = os.path.join(scripts_dir, dst, 'config', 'config.framework.yaml')
    print(dst)
    try:
        os.startfile(dst)
    except Exception as e:
        print(e)
" > batch_open_config.py

python batch_open_config.py
