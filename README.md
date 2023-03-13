[![Build Status](https://github.com/bioimage-io/model-runner-java/actions/workflows/build.yml/badge.svg)](https://github.com/bioimage-io/model-runner-java/actions/workflows/build.yml)

# Java Library for Deep Learning

This project provides a Java library for running Deep Learning (DL) models agnostically, enabling communication between Java software and various Deep Learning frameworks. It also allows the use of multiple DL frameworks in the same session.

This library is intended for developers and was originally built by the DeepIcy team as the backend of the DeepIcy plugin.

The [Java model runner](<https://github.com/bioimage-io/model-runner-java/tree/main>) is able to load models, create tensors and make inference. The library is designed in a modular way, allowing the main software to avoid dealing with the various objects and structures required by different DL frameworks. Instead the Java model runner provides interfaces for models and tensors that handle internally their creation and inference in the differnet Java DL framworks. The main software only needs to interact with the Java model runner and does not need to worry whether the model is in PyTorch or in Tensorflow

## Setting Up the Model Runner

1. Download the dependency and include it in your project

   In order to benefit from the library, include the dependency in your code. The dependency can be added manually or using a dependency manager such as Maven. If you are using Maven, add the following dependency to the project pom file:

   ```xml
   <dependency>
     <groupId>io.bioimage</groupId>
     <artifactId>dl-modelrunner</artifactId>
     <version>0.2.0</version>
   </dependency>
   ```

   and add to `<repositories>` the following:

   ```xml
   <repository>
     <id>scijava.public</id>
     <url>https://maven.scijava.org/content/groups/public</url>
   </repository>
   ```

2. Prepare the environment

   Certain pairs of DL frameworks cannot be loaded in the same classloader due to incompatible classes with the same names. For example, the Java APIs of Tensorflow 1 and Tensorflow 2 are incompatible, which has slowed the adoption of newer versions of Tensorflow in Java softwares, disrupting the connection with the latest deep learning developments.

   To address this issue, the library is designed in a modular way that creates a separate classloader for each DL framework once it is called, avoiding conflicts between the frameworks.

   To load frameworks in separate classloaders, the library requires that the executable JAR files be stored in identifiable folders, with all DL frameworks stored in the same directory. An example of this is shown in the images below:

   ![alt text](https://raw.githubusercontent.com/bioimage-io/model-runner-java/main/wiki/engines_folders.png)

   All engines should be stored in the same directory (in the example, `C:\Users\carlos\icy\engines`), with each engine following the naming convention:

   ```
   <DL_framework_name>-<python_version>-<java_api_version>-<os>-<architecture>-<cpu_if_it_runs_in_cpu>-<gpu_if_it_runs_in_gpu>
   ```

   For example, the folder `Pytorch-1.11.0-1.11.0-windows-x86_64-cpu-gpu` contains a PyTorch engine, Python version 1.11.0, same as the Java version, for Windows operating system, architecture x86_64 (64 bits), that runs in CPU and GPU.

   Another example: the folder `Tensorflow-1.12.0-1.12.0-windows-x86_64-cpu` contains a Tensorflow engine, Python version 1.12.0, same as the Java version, for Windows operating system, architecture x86_64 (64 bits), that runs only in CPU.

   The script `download-engines.py` can be used to download desired engines for your platform (OS and architecture):
   ```
   python download-engines.py <engine-filter> <version-filter>
   ```

   For example:
   ```
   python download-engines.py pytorch 1.13.1
   ```

   Running the script with no arguments downloads all available engines for your platform.

## Supported engines

Currently, the following `engine_name-interface` engines exist:

| Engine                          | Source code                                                    |
|---------------------------------|----------------------------------------------------------------|
| PyTorch                         | https://github.com/bioimage-io/pytorch-java-interface          |
| Tensorflow 1                    | https://github.com/bioimage-io/tensorflow-1-java-interface     |
| Tensorflow 2 API 0.2.0          | https://github.com/bioimage-io/tensorflow-2-java-interface-0.2 |
| Tensorflow 2 all APIs but 0.2.0 | https://github.com/bioimage-io/tensorflow-2-java-interface     |
| Onnx                            | https://github.com/bioimage-io/onnx-java-interface             |

The information about the engines supported currently by the model runner, for which OS and architectures and which JAR files are required for each of the engines is stored in [this json file](https://github.com/bioimage-io/model-runner-java/blob/main/src/main/resources/availableDLVersions.json).

Note that the model runner will be in **constant development** and that it is open to community collaboration, so **pull requests** to the official repository of the model runner to improve functionality or to add new engines are **very welcomed**.

## Implementing the Java model runner

The Java model runner was developed with the objective of being as easy as possible to implement in already existing Java softwares.
There are three key points: loading a model, creating the tensors, and making inference with the model on the tensors.

### 1. Loading a model

In order to load a model, the library needs to know first in which framework the model is going to be loaded, and then where is the model of interest.

The user needs to give information about the DL framework. For that the creation of an object called [`EngineInfo`](https://github.com/bioimage-io/model-runner-java/blob/main/src/main/java/io/bioimage/modelrunner/engine/EngineInfo.java) is required. An `EngineInfo` object has to be created with the framework name that is given by the [Bioimage.io specs](https://github.com/bioimage-io/spec-bioimage-io/blob/gh-pages/weight_formats_spec_0_4.md). **Tensorflow** should be `tensorflow_saved_model_bundled`, **PyTorch for Java**, `torchscript` and **Onnx**, `onnx`.

The other required parameters are the version of the framework in Python (sometimes it differs from the Java API version) that wants to be loaded (1.15.0, 1.9.1, 15...) and the directory where all the engines folders are stored. Looking at the previous example this directory would be `C:\Users\carlos\icy\engines`.
With this information an example code snippet would be:

```java
EngineInfo engineInfo = EngineInfo.defineDLEngine("pytorch", "1.9.1", "C:\Users\carlos\icy\engines");
```

This engine info must be used to load the corresponding model. Model loading requires 3 parameters, the model folder (directory where all the files for a model are stored), the model source (path to the file that is specified in the weights&rarr;source field in the `rdf.yaml` file) and the `EngineInfo` object previously created.

An example code to load a model would be:

```java
String modelPath = "C:\Users\carlos\icy\models\EnhancerMitochondriaEM2D_13102022_171141";
String modelSource = modelPath + "weights-torchscript.pt";
Model model = Model.createDeepLearningModel(modelPath, modelSource, engineInfo);
```

The above piece of code would call the corresponding engine instance in a separate classloader and load the model in its corresponding engine. This model can now be used to make inference.

### 2. Creating agnostic tensors

The java model runner implements its own agnostic tensors that act as a vehicle to communicate between the main Java software and the Java Deep Learning framework.

Thanks to the agnostic tensors the main program does not have to deal with the creation of different tensors depending on the DL framework, unifying the task.

Agnostic tensors use ImgLib2 `RandomAccessibleInterval`s as the backend to store the data. ImgLib2 provides an all-in-Java fast and lightweight framework to handle the data and communicate with particular Deep Learning frameworks.
The creation of tensors in the main program side is reduced to the creation of ImgLib2 `RandomAccessibleInteval`s (or objects that extend them).

Once the ImgLib2 object is created, the creation of a model runner tensor is simple. Apart from the data as ImgLib2 it requires the name of the tensor and the axes order of the tensor (as defined in the `rdf.yaml`).

An example would be:

```java
RandomAccessibleInterval<FloatType> data = ...;
Tensor tensor = Tensor.build("name", "bcyx", data);
```

Note that it is also necessary to generate the agnostic tensors that correspond to the output of the model.

These tensors are going to host the results of the inference.

Output tensors can be created as empty tensors and only contain the name and axes order of the output tensor:

```java
// Without allocation of memory
Tensor.buildEmptyTensor("outputName", "bcyx");
// Allocating memory
Tensor<FloatType> outTensor = Tensor.buildEmptyTensorAndAllocateMemory("output0",
                                                                       "bcyx",
                                                                       new long[] {1, 2, 512, 512},
                                                                       new FloatType());
```

Or can be constructed with an ImgLib2 object with the expected shape and data type of the output to allocate memory prior to execution.

```java
RandomAccessibleInterval<FloatType> expectedData = ...;
Tensor output = Tensor.build("outputName", "bcyx", expectedData);
```

### 3. Making inference

Once the model and tensors have been defined, everything is ready to make inference.

The process should be relatively easy to implement in the main software.

All the input tensors should be put together in a `List`, same for the output tensors. Then the model should be called as `model.runModel(....)`. The output list of tensors is then updated inplace.

```java
// List that will contain the input tensors
List<Tensors> inputTensors = new ArrayList<Tensor>();
// List that will contain the output tensors
List<Tensors> outputTensors = new ArrayList<Tensor>();
inputTensors.add(inputTensor);
outputTensors.add(outputTensor);
model.runModel(inputTensors, outputTensors);
// The results of applying inference will be // stored in the Tensors of the list ‘outputTensors’ variable
```

## Examples

* [ExampleLoadAndRunModel](https://github.com/bioimage-io/model-runner-java/blob/main/src/main/java/io/bioimage/modelrunner/example/ExampleLoadAndRunModel.java) (PyTorch)
* [ExampleLoadTensorflow1Tensorflow2](https://github.com/bioimage-io/model-runner-java/blob/main/src/main/java/io/bioimage/modelrunner/example/ExampleLoadTensorflow1Tensorflow2.java)
