# BFTGym - Demo

## Usage

First launch the backend server:

```bash
$ source venv/bin/activate
$ python main.py --help
usage: main.py [-h] [--host HOST] [--port PORT]

options:
  -h, --help            show this help message and exit
  --host HOST, -H HOST  host of the backend, default to be 0.0.0.0
  --port PORT, -p PORT  port of the backend, default to be 8999
```

Then launch the gradio frontend:

```bash
$ source venv/bin/activate
$ python interface.py --help
usage: interface.py [-h] [--fault FAULT] [--backend BACKEND]

options:
  -h, --help            show this help message and exit
  --fault FAULT, -f FAULT
                        f in BFT system, default is 1
  --backend BACKEND, -b BACKEND
                        backend server, default is localhost:8999
```

The default config file is `default.yaml`.

## Data flow

```mermaid
sequenceDiagram

  box BFTGym
  participant Frontend
  participant Backend
  end
  box Experiment Profile (Cloudlab)
  participant Controller_Cloudlab
  participant Workers_Cloudlab
  end


  participant Frontend as Frontend
  participant Backend as Backend
  participant Controller_Cloudlab as BFT Controller
  participant Workers_Cloudlab as BFT Workers



  rect rgb(191, 223, 255)
    note right of Frontend: Launch Experiment

    Frontend ->> Backend:  initial and Cloudlab configs

    Backend ->> Controller_Cloudlab: update config
    Backend ->> Controller_Cloudlab: launch

    Controller_Cloudlab ->> Workers_Cloudlab: instantiate
  end

  rect rgb(200, 150, 255)

  note right of Frontend: During Experiment

  Frontend ->> Backend: configs, instructions

  par
    loop  
        note over Workers_Cloudlab,Backend: BFT instances periodically fetch latest configs
        Backend -->> Controller_Cloudlab:   
        Backend -->> Workers_Cloudlab:   
    end
  and
    loop  
        note over Backend,Workers_Cloudlab: validators periodically report statistics
        Workers_Cloudlab ->> Backend: 
    end
  end

  par
    loop
        Backend -->> Frontend: periodically fetch statistics
    end
  end

  end
```
