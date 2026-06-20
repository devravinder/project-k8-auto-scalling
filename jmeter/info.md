# JMeter

## Install

- download zip & extract
- add bin path to env variable (optional)

## Creating Basic Test Config (.jmx)

1. Open the GUI
    - click on `jmeter.bat` / `jmeter.bash` in the `bin` folder

2. Create a Test Plan (once the GUI is opened)
    - create a new test plan
    - `File > New`
    - give some name

3. Add Thread Group in Test Plan
    - right click on test plan
    - `Add > Threads > Thread Group`
    - provide the necessary information

4. Add requests & headers in thread group

    1. Adding Request
        - right-click on the thread group
        - `Add > Sampler > HTTP Request`
        - add all the API data (separate IP & URI path)

    2. Adding Header
        - right-click on the thread group
        - `Add > Config Element > HTTP Header Manager`
        - then add all the headers & save

5. View Results (only for GUI) - Optional
    - right click on test plan
    - `Add > Listener > View Results Tree`

6. Save
    - click the save icon from menu
    - select the file location
    - it will save as `.jmx`

## Running Tests

### 1. GUI

- Click on the `Start` icon (Green Play button) from the top menu
- Results can be seen in `View Results Tree`

### 2. From CMD

With test results output:

```bash
jmeter -Jjmeter.save.saveservice.output_format=csv -n -t "test-plans/load-api.jmx"  -l "test-plans/load-api.csv"
```

Without test results output:

```bash
jmeter -n -t "test-plans/load-api.jmx"
```