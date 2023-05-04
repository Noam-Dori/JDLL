/*-
 * #%L
 * Use deep learning frameworks from Java in an agnostic and isolated way.
 * %%
 * Copyright (C) 2022 - 2023 Institut Pasteur and BioImage.IO developers.
 * %%
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package io.bioimage.modelrunner.bioimageio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.bioimage.modelrunner.bioimageio.description.ModelDescriptor;
import io.bioimage.modelrunner.bioimageio.download.DownloadModel;
import io.bioimage.modelrunner.bioimageio.download.DownloadTracker;
import io.bioimage.modelrunner.utils.Log;

/**
 * Class to interact with the Bioimage.io API. Used to get information
 * about models and to download them
 * @author Carlos Javier Garcia Lopez de Haro
 *
 */
public class BioimageioRepo {
	/**
	 * Message displayed when there are no models found
	 */
	private static final String MODELS_NOT_FOUND_MSG = "BioImage.io: Unable to find models.";
	/**
	 * Message displayed when there is an API error
	 */
	private static final String API_ERR_MSG = "BioImage.io: There has been an error accessing the API. No model retrieved.";
	/**
	 * URL to the file containing all the model zoo models
	 */
	public static String location = "https://raw.githubusercontent.com/bioimage-io/collection-bioimage-io/gh-pages/collection.json";
	/**
	 * JSon containing all the info about the Bioimage.io models
	 */
	private JsonArray collections;
	/**
	 * List of all the model IDs of the models existing in the BioImage.io
	 */
	private static List<String> modelIDs;
	
	private Map<Path, ModelDescriptor> models;
	
	private Consumer<String> consumer;
	
	/**
	 * 
	 */
	private BioimageioRepo() {
		setCollectionsRepo();
	}
	
	/**
	 * 
	 * @param consumer
	 */
	private BioimageioRepo(Consumer<String> consumer) {
		this.consumer = consumer;
		setCollectionsRepo();
	}
	
	/**
	 * Create an instance of the models stored in the Bioimage.io repository reading the 
	 * collections rdf.yaml.
	 * @return an instance of the {@link BioimageioRepo}
	 */
	public static BioimageioRepo connect() {
		return new BioimageioRepo();
	}
	
	/**
	 * Create an instance of the models stored in the Bioimage.io repository reading the 
	 * collections rdf.yaml.
	 * @return an instance of the {@link BioimageioRepo}
	 */
	public static BioimageioRepo connect(Consumer<String> consumer) {
		return new BioimageioRepo(consumer);
	}
	
	/**
	 * 
	 * @param args
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		BioimageioRepo br = new BioimageioRepo();
		br.downloadModelByID("10.5281/zenodo.5874741", "C:\\Users\\angel\\OneDrive\\Documentos\\pasteur\\git\\deep-icy\\models");
	}
	
	/**
	 * Method that connects to the BioImage.io API and retrieves the models available
	 * at the Bioimage.io model repository
	 * @return an object containing the zip location of the model as key and the {@link ModelDescriptor}
	 * 	with the yaml file information in the value
	 */
	public Map<Path, ModelDescriptor> listAllModels() {
		return listAllModels(true);
	}
	
	/**
	 * Method that connects to the BioImage.io API and retrieves the models available
	 * at the Bioimage.io model repository
	 * @return an object containing the zip location of the model as key and the {@link ModelDescriptor}
	 * 	with the yaml file information in the value
	 */
	public Map<Path, ModelDescriptor> listAllModels(boolean verbose) {
		if (models != null && models.entrySet().size() > 0)
			return models;
		if (verbose)
			Log.addProgressAndShowInTerminal(consumer, "BioImage.io: Accessing the BioImage.io API to retrieve available models", true);
		models = new HashMap<Path, ModelDescriptor>();
		if (collections == null) {
			if (verbose)
				Log.addProgressAndShowInTerminal(consumer, MODELS_NOT_FOUND_MSG, true);
			return models;
		}
		for (Object resource : collections) {
			if (Thread.interrupted())
				break;
			Path modelPath = null;
			JsonObject jsonResource = (JsonObject) resource;
			try {
				if (jsonResource.get("type") == null || !jsonResource.get("type").getAsString().equals("model"))
					continue;
				String stringRDF = getJSONFromUrl(jsonResource.get("rdf_source").getAsString());
				modelPath = createPathFromURLString(jsonResource.get("rdf_source").getAsString());
				ModelDescriptor descriptor = ModelDescriptor.readFromYamlTextString(stringRDF, verbose);
				models.put(modelPath, descriptor);
			} catch (Exception ex) {
				// TODO Maybe add some error message? This should be responsibility of the BioImage.io user
				// Only display error message if there was an error creating
				// the descriptor from the yaml file
				if (modelPath != null && verbose) {
					String errMSg = "Could not load descriptor for the Bioimage.io model " + modelPath.getFileName() + ": " + ex.toString();
					Log.addProgressAndShowInTerminal(consumer, errMSg, true);
				}
                ex.printStackTrace();
			}
		}
		return models;
	}
	
	/**
	 * Method that stores all the model IDs for the models available in the BIoImage.io repo
	 */
	private void setCollectionsRepo() {
		modelIDs = new ArrayList<String>();
		String text = getJSONFromUrl(location);
		if (text == null) {
			Log.addProgressAndShowInTerminal(consumer, MODELS_NOT_FOUND_MSG, true);
			Log.addProgressAndShowInTerminal(consumer, "BioImage.io: Cannot access file: " + location, true);
			Log.addProgressAndShowInTerminal(consumer, "BioImage.io: Please review the certificates needed to access the website.", true);
			return;
		}
		JsonObject json = null;
		try {
			json = (JsonObject) JsonParser.parseString(text);
		} catch (Exception ex) {
			collections = null;
			Log.addProgressAndShowInTerminal(consumer, MODELS_NOT_FOUND_MSG, true);
			return;
		}
		// Iterate over the array corresponding to the key: "resources"
		// which contains all the resources of the Bioimage.io
		collections = (JsonArray) json.get("collection");
		if (collections == null) {
			Log.addProgressAndShowInTerminal(consumer, MODELS_NOT_FOUND_MSG, true);
			return;
		}
		for (Object resource : collections) {
			JsonObject jsonResource = (JsonObject) resource;
			if (jsonResource.get("type") == null || !jsonResource.get("type").getAsString().equals("model"))
				continue;
			String modelID = jsonResource.get("id").getAsString();
			modelIDs.add(modelID);
		}
	}

	/**
	 * Method used to read a yaml or json file from a server as a raw string
	 * @param url
	 * 	String url of the file
	 * @return a String representation of the file. It is null if the file was not accessed
	 */
	private static String getJSONFromUrl(String url) {
		return getJSONFromUrl(url, null);
	}

	/**
	 * Method used to read a yaml or json file from a server as a raw string
	 * @param url
	 * 	String url of the file
	 * @param consumer
	 * 	object to communicate with the main interface
	 * @return a String representation of the file. It is null if the file was not accessed
	 */
	private static String getJSONFromUrl(String url, Consumer<String> consumer) {

		HttpsURLConnection con = null;
		try {
			URL u = new URL(url);
			con = (HttpsURLConnection) u.openConnection();
			con.connect();
			InputStream inputStream = con.getInputStream();
			
			 ByteArrayOutputStream result = new ByteArrayOutputStream();
			 byte[] buffer = new byte[1024];
			 for (int length; (length = inputStream.read(buffer)) != -1; ) {
			     result.write(buffer, 0, length);
			 }
			 // StandardCharsets.UTF_8.name() > JDK 7
			 String txt = result.toString("UTF-8");
			 inputStream.close();
			 result.close();
			 return txt;
		} 
		catch (IOException ex) {
			Log.addProgressAndShowInTerminal(consumer, API_ERR_MSG, true);
			ex.printStackTrace();
		} 
		finally {
			if (con != null) {
				try {
					con.disconnect();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
		return null;
	}
	
	/**
	 * Create {@link Path} from Url String. This method removes the http:// or https://
	 * at the begining because in windows machines it caused errors creating Paths
	 * @param downloadUrl
	 * 	String url of the model of interest
	 * @return the path to the String url
	 */
	public static Path createPathFromURLString(String downloadUrl) {
		Path path;
		try {
			if (downloadUrl.startsWith("https://")) {
				downloadUrl = downloadUrl.substring(("https://").length());
			} else if (downloadUrl.startsWith("http://")) {
				downloadUrl = downloadUrl.substring(("http://").length());
			}
			path = new File(downloadUrl).toPath();
		} catch (Exception ex) {
			int startName = downloadUrl.lastIndexOf("/");
			downloadUrl = downloadUrl.substring(startName + 1);
			path = new File(downloadUrl).toPath();
		}
		return path;
	}
	
	/**
	 * Return a list with all the model IDs for the models existing in the Bioimage.io repo
	 * @return list with the ids for each of the models in the repo
	 */
	public static List<String> getModelIDs(){
		return modelIDs;
	}
	
	/**
	 * Return the {@link ModelDescriptor} for the model defined by the modelID
	 * (field 'id' in the rdf.yaml) introduced as a parameter.
	 * @param modelID
	 * 	unique ID for each Bioimage.io model
	 * @return the {@link ModelDescriptor} of the model
	 */
	public ModelDescriptor selectByID(String modelID) {
		Entry<Path, ModelDescriptor> modelEntry = this.listAllModels(false).entrySet().stream()
				.filter(ee -> {
					String id = ee.getValue().getModelID();
					if (id.length() - id.replace("/", "").length() == 2) {
						id = id.substring(0, id.lastIndexOf("/"));
					}
					String reqId = modelID;
					if (modelID.length() - modelID.replace("/", "").length() == 2) {
						return modelID.substring(0, modelID.lastIndexOf("/")).equals(id);
					}
					return modelID.equals(id);
				}).findFirst().orElse(null);
		if (modelEntry != null)
			return modelEntry.getValue();
		return null;
	}
	
	/**
	 * Return the {@link ModelDescriptor} for the model defined by the name
	 * (field 'name' in the rdf.yaml) introduced as a parameter.
	 * @param name
	 * 	unique name for each Bioimage.io model
	 * @return the {@link ModelDescriptor} of the model
	 */
	public ModelDescriptor selectByName(String name) {
		Entry<Path, ModelDescriptor> modelEntry = this.listAllModels(false).entrySet().stream()
				.filter(ee -> ee.getValue().getName().equals(name)).findFirst().orElse(null);
		if (modelEntry != null)
			return modelEntry.getValue();
		return null;
		
	}
	
	/**
	 * Return the {@link ModelDescriptor} for the model defined by the url to the rdf file
	 * (field 'rdf_source' in the rdf.yaml) introduced as a parameter.
	 * @param rdfURL
	 * 	unique url of the rdf file of each Bioimage.io model
	 * @return the {@link ModelDescriptor} of the model
	 */
	public ModelDescriptor selectByRdfSource(String rdfURL) {
		Entry<Path, ModelDescriptor> modelEntry = this.listAllModels(false).entrySet().stream()
				.filter(ee -> ee.getValue().getRDFSource().equals(rdfURL)).findFirst().orElse(null);
		if (modelEntry != null)
			return modelEntry.getValue();
		return null;
	}
	
	/**
	 * 
	 * @param descriptor
	 * @param modelsDirectory
	 * @param consumer
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void downloadModel(ModelDescriptor descriptor, String modelsDirectory, 
			DownloadTracker.TwoParameterConsumer<String, Double> consumer) throws IOException, InterruptedException {
		DownloadModel dm = DownloadModel.build(descriptor, modelsDirectory);
		Thread downloadThread = new Thread(() -> {
			try {
				dm.downloadModel();
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
        });
		if (consumer == null)
			consumer = new DownloadTracker.TwoParameterConsumer<String, Double>();
		DownloadTracker mdt = DownloadTracker.getBMZModelDownloadTracker(consumer, dm, downloadThread);
		downloadThread.start();
		Thread trackerThread = new Thread(() -> {
            try {
				mdt.track();
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
        });
		trackerThread.start();
		printProgress(downloadThread, consumer);
		List<String> badDownloads = new ArrayList<String>();
		for (String link : dm.getListOfLinks()) {
			String name = link.substring(link.lastIndexOf("/") + 1);
			if (consumer.get().get(dm.getModelFolder() + File.separator + name) == null
					|| consumer.get().get(dm.getModelFolder() + File.separator + name) != 1.0)
				badDownloads.add(link);
		}
		
		if (badDownloads.size() > 0)
			throw new IOException("The following files of model '" + descriptor.getName()
			+ "' where downloaded incorrectly: " + badDownloads.toString());
	}
	
	/**
	 * Method that tracks the progress of a download happening in the 
	 * thread used as the first parameter and being tracked by the consumer
	 * used as the second parameter.
	 * The teminal output should look like the following for every file:
	 * 	file1.txt: [#######...................] 10%
	 * 
	 * @param downloadThread
	 * 	thread where the download is happening, when it stops the tracking stops
	 *  too
	 * @param consumer
	 * 	consumer that provides the info about the download
	 * @throws InterruptedException if the thread is stopped by other thread while it is sleeping
	 */
	public static void printProgress(Thread downloadThread,
			DownloadTracker.TwoParameterConsumer<String, Double> consumer) throws InterruptedException {
		int n = 30;
		String ogProgressStr = "";
		String ogRemainingStr = "";
		for (int i = 0; i < n; i ++) {
			ogProgressStr += "#";
			ogRemainingStr += ".";
		}
		List<String> already = new ArrayList<String>();
		while (downloadThread.isAlive()) {
			Thread.sleep(3000);
			String select = null;
			for (String key : consumer.get().keySet()) {
				if (!already.contains(key) && !key.equals(DownloadTracker.TOTAL_PROGRESS_KEY)) {
					select = key;
					break;
				}
			}
			if (select == null)
				continue;
			for (String kk : new String[] {select, DownloadTracker.TOTAL_PROGRESS_KEY}) {
				int nProgressBar = (int) (consumer.get().get(kk) * n);
				String progressStr = new File(kk).getName() + ": [" 
					+ ogProgressStr.substring(0, nProgressBar) + ogRemainingStr.substring(nProgressBar)
					+ "] " + Math.round(consumer.get().get(kk) * 100) + "%";
				System.out.println(progressStr);
			}
			if (consumer.get().get(select) == 1 || consumer.get().get(select) < 0)
				already.add(select);
		}
	}
	/**
	 * 
	 * @param id
	 * @param modelsDirectory
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void downloadModelByID(String id, String modelsDirectory) throws IOException, InterruptedException {
		ModelDescriptor model = selectByID(id);
		if (model == null)
			throw new IllegalArgumentException("");
		downloadModel(model, modelsDirectory, null);
	}
	
	/**
	 * 
	 * @param id
	 * @param modelsDirectory
	 * @param consumer
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void downloadModelByID(String id, String modelsDirectory, 
			DownloadTracker.TwoParameterConsumer<String, Double> consumer) throws IOException, InterruptedException {
		ModelDescriptor model = selectByID(id);
		if (model == null)
			throw new IllegalArgumentException("");
		downloadModel(model, modelsDirectory, consumer);
	}
	
	/**
	 * 
	 * @param name
	 * @param modelsDirectory
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void downloadByName(String name, String modelsDirectory) throws IOException, InterruptedException {
		ModelDescriptor model = selectByName(name);
		if (model == null)
			throw new IllegalArgumentException("");
		downloadModel(model, modelsDirectory, null);
	}
	
	/**
	 * 
	 * @param name
	 * @param modelsDirectory
	 * @param consumer
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void downloadByName(String name, String modelsDirectory, 
			DownloadTracker.TwoParameterConsumer<String, Double> consumer) throws IOException, InterruptedException {
		ModelDescriptor model = selectByName(name);
		if (model == null)
			throw new IllegalArgumentException("");
		downloadModel(model, modelsDirectory, consumer);
	}
	
	/**
	 * 
	 * @param rdfUrl
	 * @param modelsDirectory
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void downloadByRdfSource(String rdfUrl, String modelsDirectory) throws IOException, InterruptedException {
		ModelDescriptor model = selectByRdfSource(rdfUrl);
		if (model == null)
			throw new IllegalArgumentException("");
		downloadModel(model, modelsDirectory, null);
	}
	
	/**
	 * 
	 * @param rdfUrl
	 * @param modelsDirectory
	 * @param consumer
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void downloadByRdfSource(String rdfUrl, String modelsDirectory, 
			DownloadTracker.TwoParameterConsumer<String, Double> consumer) throws IOException, InterruptedException {
		ModelDescriptor model = selectByRdfSource(rdfUrl);
		if (model == null)
			throw new IllegalArgumentException("");
		downloadModel(model, modelsDirectory, consumer);
	}
}
