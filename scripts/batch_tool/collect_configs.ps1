$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'

mkdir collect

echo "import os

scripts_dir = os.path.dirname(os.path.abspath(__file__))
exp_name = os.path.basename(scripts_dir)
dst_list = os.listdir(scripts_dir)
dst_list.sort()

for dst in dst_list:
    dst_full = os.path.join(scripts_dir, dst, 'config', 'config.framework.yaml')
    print(dst_full)
    try:
        with open(dst_full, 'r', encoding='utf8') as f:
            contents = f.read()
        with open(os.path.join('collect', exp_name + '-' + dst + '.yaml'), 'w', encoding='utf8') as f:
            f.write(contents)
    except Exception as e:
        print(e)
" > collect_configs.py

python collect_configs.py
