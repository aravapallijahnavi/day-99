import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MultithreadedFileSearcher extends Frame {
    private TextField directoryField;
    private TextField keywordField;
    private TextArea resultArea;
    private Button searchButton;

    private AtomicInteger totalFilesScanned;
    private AtomicInteger matchesFound;
    private AtomicLong startTime;
    private AtomicLong endTime;

    public MultithreadedFileSearcher() {
        setLayout(new FlowLayout());

        Label directoryLabel = new Label("Directory:");
        add(directoryLabel);
        directoryField = new TextField(30);
        add(directoryField);

        Label keywordLabel = new Label("Keyword:");
        add(keywordLabel);
        keywordField = new TextField(20);
        add(keywordField);

        searchButton = new Button("Search");
        add(searchButton);

        resultArea = new TextArea(20, 60);
        add(resultArea);

        searchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String directory = directoryField.getText().isEmpty() ? "C:/TestDirectory" : directoryField.getText();
                String keyword = keywordField.getText().isEmpty() ? "example_keyword" : keywordField.getText();
                resultArea.setText("");
                startTime = new AtomicLong(System.currentTimeMillis());
                totalFilesScanned = new AtomicInteger(0);
                matchesFound = new AtomicInteger(0);
                searchFiles(directory, keyword);
            }
        });

        setTitle("Multithreaded File Searcher");
        setSize(700, 500);
        setVisible(true);
    }

    public void searchFiles(String directoryPath, String keyword) {
        File directory = new File(directoryPath);
        if (!directory.exists() || !directory.isDirectory()) {
            resultArea.append("Directory not found: " + directoryPath + "\n");
            return;
        }

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(countFiles(directory));
        searchDirectory(directory, keyword, executorService, latch);
        try {
            latch.await(); // Wait for all tasks to finish
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        executorService.shutdown();
        endTime = new AtomicLong(System.currentTimeMillis());
        displaySummary();
    }

    private void searchDirectory(File directory, String keyword, ExecutorService executorService, CountDownLatch latch) {
        File[] files = directory.listFiles();
        if (files == null) {
            resultArea.append("Read permission error for directory: " + directory.getPath() + "\n");
            latch.countDown();
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                executorService.execute(() -> {
                    searchDirectory(file, keyword, executorService, latch);
                    latch.countDown();
                });
            } else {
                executorService.execute(() -> {
                    searchFile(file, keyword);
                    latch.countDown();
                });
            }
        }
    }

    private void searchFile(File file, String keyword) {
        totalFilesScanned.incrementAndGet();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean matchFound = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains(keyword)) {
                    matchesFound.incrementAndGet();
                    matchFound = true;
                    break;
                }
            }
            synchronized (resultArea) {
                if (matchFound) {
                    resultArea.append("[" + Thread.currentThread().getName() + "] Keyword found in file: " + file.getPath() + "\n");
                } else {
                    resultArea.append("[" + Thread.currentThread().getName() + "] No keyword in file: " + file.getPath() + "\n");
                }
            }
        } catch (IOException e) {
            synchronized (resultArea) {
                resultArea.append("[" + Thread.currentThread().getName() + "] Error reading file: " + file.getPath() + "\n");
            }
        }
    }

    private void displaySummary() {
        long timeTaken = endTime.get() - startTime.get();
        resultArea.append("\nSummary:\n");
        resultArea.append("Total files scanned: " + totalFilesScanned.get() + "\n");
        resultArea.append("Matches found: " + matchesFound.get() + "\n");
        resultArea.append("Time taken: " + timeTaken + " ms\n");
    }

    private int countFiles(File directory) {
        int count = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    count += countFiles(file);
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    public static void main(String[] args) {
        new MultithreadedFileSearcher();
    }
}
