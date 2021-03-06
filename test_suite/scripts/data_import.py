import sys
import time
import xml.etree.ElementTree as ElementTree

import requests

namespaces = {
    'core': 'http://www.enactor.com/core',
    'soapenv': 'http://www.w3.org/2003/05/soap-envelope',
    'ser': 'http://service.coreProcessing.enactor.com'
}

filePaths = []

empHost = sys.argv[1]
emsHost = sys.argv[2]
empPort = sys.argv[3]  # 39832
emsPort = sys.argv[4]  # 39833
filePaths = sys.argv[5:]

empFileImportUrl = "http://{}:{}/WebCore/FileRepo/ManualImports/".format(empHost, empPort)
emsScheduledJobServiceUrl = "http://{}:{}/axis2/services/ScheduledJobService?WSDL".format(emsHost,
                                                                                          emsPort)


def upload_file(fileName, filePath):
    files = {'file': open(filePath, 'rb')}
    print("Uploading File: {}({}) to Repository".format(fileName, filePath))
    response = requests.post("{}/{}".format(empFileImportUrl, fileName), files=files, timeout=20)
    print('Response: {}'.format(response.status_code))
    print("Sent File: {}".format(filePath))


def schedule_import(fileName):
    request = """<?xml version="1.0" encoding="UTF-8"?>
    <soap:Envelope
        xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
        xmlns:core="http://www.enactor.com/core"
        xmlns:ser="http://service.coreProcessing.enactor.com"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
    <soap:Header>
    </soap:Header>
    <soap:Body>
        <ser:scheduleProcessInvocation>
            <core:scheduledProcessInvokeRequest>
                <core:processId>EstateDirector/Imports/FileImporterJob</core:processId>
                <core:replyToConnectedProcessId>RetailProcessServer</core:replyToConnectedProcessId>
                <core:replyToConnectionPointId>RetailServerJobResults</core:replyToConnectionPointId>
                <core:inputData>
                    <core:ignoreInvalidSerialization>false</core:ignoreInvalidSerialization>
                    <core:dataItem>
                        <core:data xsi:type="core:String">{}</core:data>
                        <core:type name="enactor.commonUI.FileName">
                        <core:interfaceName>java.lang.String</core:interfaceName>
                        </core:type>
                    </core:dataItem>
                </core:inputData>
            </core:scheduledProcessInvokeRequest>
        </ser:scheduleProcessInvocation>
    </soap:Body>
    </soap:Envelope>""".format(fileName)

    print("Scheduling file: {} for import".format(fileName))

    response = requests.post(emsScheduledJobServiceUrl, data=request, timeout=20)
    print('Response Code from {}: {}'.format(emsScheduledJobServiceUrl, response.status_code))
    print("Created Scheduled Job for: {}".format(fileName))

    root = ElementTree.fromstring(response.content)
    results = root.findall('./soapenv:Body'
                           '/ser:scheduleProcessInvocationResponse'
                           '/core:scheduledProcessInvokeResponse'
                           '/core:job'
                           '/core:jobId', namespaces=namespaces)
    # print(results)
    if len(results) != 1:
        raise Exception("Unable to obtain jobId for file: {}".format(fileName))

    return results[0].text


def check_status(job_id):
    request = """<?xml version="1.0" encoding="UTF-8"?>
    <soap:Envelope
        xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
        xmlns:core="http://www.enactor.com/core"
        xmlns:ser="http://service.coreProcessing.enactor.com">
    <soap:Header>
    </soap:Header>
    <soap:Body>
        <ser:scheduleProcessStatus>
            <core:scheduledProcessStatusRequest>
                <core:jobId>{}</core:jobId>
            </core:scheduledProcessStatusRequest>
        </ser:scheduleProcessStatus>
    </soap:Body>
    </soap:Envelope>
    """.format(job_id)

    response = requests.post(emsScheduledJobServiceUrl, data=request, timeout=20)
    # print('Response Code from {}: {}'.format(url, response.status_code))
    root = ElementTree.fromstring(response.content)
    results = root.findall('./soapenv:Body'
                           '/ser:scheduleProcessStatusResponse'
                           '/core:scheduledProcessStatusResponse'
                           '/core:job'
                           '/core:status', namespaces=namespaces)
    # results = root.findall('./', namespaces=namespaces)
    # print(results)
    for result in results:
        return result.text

    return None


def import_file(file_name, file_path):
    upload_file(file_name, file_path)
    job_id = schedule_import(file_name)
    status = check_status(job_id)
    while status in ['QUEUED', 'ACTIVE', 'QUEUED_FOR_RETRY']:
        status = check_status(job_id)
        print('{} status: {}'.format(file_name, status))
        if status is None:
            break
        time.sleep(20)

    if status == 'SUCCESSFUL':
        return True
    else:
        return False


failedFiles = []
count = 0
for filePath in filePaths:
    fileName = filePath.split("/")[-1]
    count += 1
    successful = import_file(fileName, filePath)
    if not successful:
        failedFiles.append(fileName)

    print("\n")

if len(failedFiles) > 0:
    print("!!!!!!!! {} files failed to be imported !!!!!!!!!! \n[{}]".format(len(failedFiles),
                                                                             failedFiles))
percentage = float(len(failedFiles)) / float(count)
if percentage > 0.1:
    print('{} percent of files failed to imported'.format(percentage))
    sys.exit(1)
