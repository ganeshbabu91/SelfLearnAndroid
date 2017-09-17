package com.android.apprepo.ganesh.selflearnandroid.service;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Created by ganesh on 4/19/17.
 */

public class SelfLearnImageDetection {

    private static final String GOOGLE_CLOUD_VISION_API_KEY = "AIzaSyCRs5m3Hp3rlVQXxI6g3sDdBxYzyAuIP7g";

    public static String invokeGoogleVisionAPI(final Bitmap bitmap){
        // Process the bitmap to convert to bytes
        if (bitmap==null) {
            Log.d("invokeGoogleVisionAPI", "Empty bitmap image");
            return "Invalid image";
        }
        Log.d("invokeGoogleVisionAPI", "Bitmap image received");
        Image image = convertBitmapToByteString(bitmap);

        // Execute the async task as this is network operation
        // Otherwise, android.os.NetworkOnMainThreadException will be thrown at runtime

        String responseString = null;
        try {
            responseString = new SelfLearnImageDetectionTask().execute(image).get();
        } catch (InterruptedException e) {
            Log.d("invokeGoogleVisionAPI", "InterruptedException while executing async task");
        } catch (ExecutionException e) {
            Log.d("invokeGoogleVisionAPI", "ExecutionException while executing async task");
        }
        Log.d("invokeGoogleVisionAPI", "Successfully returned the response back to screen");

        return responseString;
    }

    private static Image convertBitmapToByteString(Bitmap bitmap) {
        Image image = new Image();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG,90,byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        image.encodeContent(imageBytes);
        return image;
    }

    private static class SelfLearnImageDetectionTask extends AsyncTask<Object, Void, String> {

        @Override
        protected String doInBackground(Object... params) {

            // Performs authorization and returns the authorized handler for Vision API
            Vision vision = performAppAuthorization();

            // Build the images annotation request
            List<AnnotateImageRequest> requests = new ArrayList<>();
            AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();
            annotateImageRequest.setImage((Image)params[0]);

            // Add label detection feature on the request
            List<Feature> features = new ArrayList<>();
            Feature feature = new Feature();
            feature.setType("LABEL_DETECTION");
            features.add(feature);
            annotateImageRequest.setFeatures(features);

            requests.add(annotateImageRequest);

            // Construct BatchAnnotationImagesRequest
            BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
            batchAnnotateImagesRequest.setRequests(requests);

            Log.d("doInBackground", "Annotation image request constructed with label detection feature");

            // Send the request to API and get the response
            Vision.Images.Annotate visonImagesAnnotate;
            BatchAnnotateImagesResponse batchAnnotateImagesResponse;
            try {
                visonImagesAnnotate = vision.images().annotate(batchAnnotateImagesRequest);
                Log.d("doInBackground","Executing the request, waiting for the response...");
                batchAnnotateImagesResponse = visonImagesAnnotate.execute();

            } catch (IOException e) {
                Log.e("doInBackground","Error while annotating batch images request");
                e.printStackTrace();
                return "Something went wrong. Try again later!";
            }

            // Extract the label detection information from the response
            Log.d("doInBackground", "Response ready. Parsing all the responses...");

            return parseVisionResponse(batchAnnotateImagesResponse);
        } // End of doInBackground

        private String parseVisionResponse(BatchAnnotateImagesResponse response) {
            StringBuilder responseBuilder = new StringBuilder();
            List<EntityAnnotation> labelAnnotations = response.getResponses().get(0).getLabelAnnotations();
            if (labelAnnotations == null || labelAnnotations.isEmpty()){
                return "Oops! We can't detect what you captured. It could be because of poor lighting or image quality. Try another!";
            }
            for (EntityAnnotation labelAnnotation : labelAnnotations){
                responseBuilder.append("Score : " + labelAnnotation.getScore() + "\n").append("Confidence : " + labelAnnotation.getConfidence() + "\n").append("Description : ").append(labelAnnotation.getDescription()).append("\n");
            }
            return responseBuilder.toString();
        }

        private Vision performAppAuthorization() {
            VisionRequestInitializer requestInitializer =
                    new VisionRequestInitializer(GOOGLE_CLOUD_VISION_API_KEY) {
                        /**
                         * We override this so we can inject important identifying fields into the HTTP
                         * headers. This enables use of a restricted cloud platform API key.
                         */
                        @Override
                        protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                                throws IOException {
                            super.initializeVisionRequest(visionRequest);

                            /*String packageName = getPackageName();
                            visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                            String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);

                            visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);*/
                        }
                    };
            Vision.Builder builder = new Vision.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance(), null);
            builder.setVisionRequestInitializer(requestInitializer);

            return builder.build();
        }
    } // End of Inner Class
}
