/*******************************************************************************
 * Copyright (c) 2020, 2020 IBM Corp. and others
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] http://openjdk.java.net/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0 OR LicenseRef-GPL-2.0 WITH Assembly-exception
 *******************************************************************************/

#include <atomic>
#include <iostream>
#include <mutex>
#include <jvmti.h>
#include <ibmjvmti.h>
#include <map>
#include "agentOptions.hpp"
#include "infra.hpp"
#include "json.hpp"

//how many times
using json = nlohmann::json;

std::atomic<bool> stackTraceEnabled{true};
std::atomic<int> monitorSampleRate{1};
std::atomic<int> monitorSampleCount{0};
std::atomic<int> monitorStackTraceDepth{0};

void setMonitorStackTrace(bool val)
{
    /* Enables or disables the stack trace option */
    stackTraceEnabled = val;
}

void setMonitorSampleRate(int rate)
{
    if (rate > 0)
    {
        stackTraceEnabled = true;
        monitorSampleRate = rate;
    }
    else
    {
        stackTraceEnabled = false;
    }
}

static const int MAX_TRACE_DEPTH = 128;

void setMonitorStackTraceDepth(int stackTraceDepth)
{
    /* Refuse to generate more than 128 deep */
    if (stackTraceDepth > MAX_TRACE_DEPTH)
       stackTraceDepth = MAX_TRACE_DEPTH;
    monitorStackTraceDepth = stackTraceDepth;
}

JNIEXPORT void JNICALL MonitorContendedEntered(jvmtiEnv *jvmtiEnv, JNIEnv *env, jthread thread, jobject object){
    json j;
    jvmtiError error;

    /* Get number of events and increment */
    int numSamples = atomic_fetch_add(&monitorSampleCount, 1);
    if (numSamples % monitorSampleRate != 0)
        return;

    static std::map<std::string, int> numContentions;
    jclass cls = env->GetObjectClass(object);
    /* First get the class object */
    jmethodID mid = env->GetMethodID(cls, "getClass", "()Ljava/lang/Class;");
    jobject clsObj = env->CallObjectMethod(object, mid);
    /* Now get the class object's class descriptor */
    cls = env->GetObjectClass(clsObj);
    /* Find the getName() method on the class object */
    mid = env->GetMethodID(cls, "getName", "()Ljava/lang/String;");
    /* Call the getName() to get a jstring object back */ 
    jstring strObj = (jstring)env->CallObjectMethod(clsObj, mid);
    /* Now get the c string from the java jstring object */
    const char *str = env->GetStringUTFChars(strObj, NULL);
    /* record calling class */
    j["Class"] = str;
    
    /* free str */
   
    static std::mutex contentionMutex;
    int samplesPerObjectType = 0;
    {
    std::lock_guard<std::mutex> lg(contentionMutex);
    samplesPerObjectType = ++(numContentions[std::string(str)]);
    }
    j["numTypeContentions"] = samplesPerObjectType;
    /* Release the memory pinned char array */
    env->ReleaseStringUTFChars(strObj, str);
    env->DeleteLocalRef(cls);

    if (monitorStackTraceDepth > 0)
    {
        jvmtiError err;
        jvmtiFrameInfo frames[MAX_TRACE_DEPTH];
        jint count;
        err = jvmtiEnv->GetStackTrace(thread, 0, monitorStackTraceDepth, frames, &count);
        if (check_jvmti_error(jvmtiEnv, err, "Unable to retrieve Stack Trace.") && count >= 1)
        {
            auto jMethods = json::array();
            bool error = false;
            for (int i=0; i < count && !error; i++)
            {
                char *methodName;
                char *signature;
                err = jvmtiEnv->GetMethodName(frames[i].method, &methodName, &signature, NULL);
                if (check_jvmti_error(jvmtiEnv, err, "Unable to retrieve Method Name.\n")) 
                {
                    jclass declaring_class;
                    err = jvmtiEnv->GetMethodDeclaringClass(frames[i].method, &declaring_class);
                    if (check_jvmti_error(jvmtiEnv, err, "Unable to retrieve Method Declaring Class.\n")) 
                    {
                        char *declaringClassName;
                        err = jvmtiEnv->GetClassSignature(declaring_class, &declaringClassName, NULL);
                        if (check_jvmti_error(jvmtiEnv, err, "Unable to retrieve Method Declaring Class Signature.\n")) 
                        {
                            json jMethod;
                            jMethod["class"] = declaringClassName;
                            jMethod["method"] = methodName;
                            jMethod["signature"] = signature;
                            jMethods.push_back(jMethod);
                            err = jvmtiEnv->Deallocate((unsigned char*)declaringClassName);
                            check_jvmti_error(jvmtiEnv, err, "Unable to deallocate class name.\n");
                        }
                        else
                        {
                            error = true;
                        }
                    }
                    else
                    {
                        error = true;
                    }
                    err = jvmtiEnv->Deallocate((unsigned char*)methodName);
                    check_jvmti_error(jvmtiEnv, err, "Unable to deallocate methodName.\n");
                    err = jvmtiEnv->Deallocate((unsigned char*)signature);
                    check_jvmti_error(jvmtiEnv, err, "Unable to deallocate method signature.\n");
                }
                else
                {
                    error = true;
                }
            } /* end for loop */
            j["stackTrace"] = jMethods;
        }
    }
    /* Get the thread name */
    jvmtiThreadInfo threadInfo;
    error = jvmtiEnv->GetThreadInfo(thread, &threadInfo);
    if (check_jvmti_error(jvmtiEnv, error, "Unable to retrieve thread info."))
    {
        j["threadName"] = threadInfo.name;
        error = jvmtiEnv->Deallocate((unsigned char*)(threadInfo.name));
        check_jvmti_error(jvmtiEnv, error, "Unable to deallocate thread name.\n");
        // Local JNI refs to threadInfo.thread_group and threadInfo.context_class_loader will be freed upon return
    }

    /* Get OS thread ID 
     * We need to use OpenJ9 JVMTI extension functions
     */
    jint extensionFunctionCount = 0;
    jvmtiExtensionFunctionInfo *extensionFunctions = NULL;
    jvmtiEnv->GetExtensionFunctions(&extensionFunctionCount, &extensionFunctions);
    for (int i=0; i < extensionFunctionCount; i++) /* search for desired function */
    {
        jvmtiExtensionFunction function = extensionFunctions->func;
        if (strcmp(extensionFunctions->id, COM_IBM_GET_OS_THREAD_ID) == 0)
        {
            jlong threadID = 0;
            /* jvmtiError GetOSThreadID(jvmtiEnv* jvmti_env, jthread thread, jlong * threadid_ptr); */
            error = function(jvmtiEnv, thread, &threadID);
            if (check_jvmti_error(jvmtiEnv, error, "Unable to retrieve thread ID."))
            {
                j["threadID"] = threadID;
            }
            break;
        }
        extensionFunctions++; /* move on to the next extension function */
    }
    
    sendToServer(j.dump());
}
