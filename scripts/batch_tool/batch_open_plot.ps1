$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'

echo "import os

scripts_dir = os.path.dirname(os.path.abspath(__file__))
dst_list = os.listdir(scripts_dir)
dst_list.sort()

for dst in dst_list:
    dst = os.path.join(scripts_dir, dst, 'plot.pdf')
    print(dst)
    try:
        os.startfile(dst)
    except Exception as e:
        print(e)
" > batch_open_plot.py

python batch_open_plot.py
