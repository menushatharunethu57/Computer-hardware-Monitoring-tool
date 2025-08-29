package me.example;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HardwareMonitorPDF {

    public static void main(String[] args) throws Exception {
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        OperatingSystem os = si.getOperatingSystem();

        String pdfPath = "HardwareReport.pdf";
        PdfWriter writer = new PdfWriter(pdfPath);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        // System Info
        document.add(new Paragraph("=== System Information ==="));
        document.add(new Paragraph(os.toString()));

        CentralProcessor cpu = hal.getProcessor();
        document.add(new Paragraph("CPU: " + cpu.getProcessorIdentifier().getName()));
        document.add(new Paragraph("Logical CPUs: " + cpu.getLogicalProcessorCount()));
        document.add(new Paragraph("Physical CPU packages: " + cpu.getPhysicalPackageCount()));

        // CPU Load
        document.add(new Paragraph("\n=== CPU Load ==="));
        long[] prevTicks = cpu.getSystemCpuLoadTicks();
        TimeUnit.SECONDS.sleep(1);
        double load = cpu.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0;
        document.add(new Paragraph(String.format("System CPU Load: %.1f %%", load)));

        // Memory
        GlobalMemory mem = hal.getMemory();
        document.add(new Paragraph("\n=== Memory ==="));
        document.add(new Paragraph("Total RAM: " + toMiB(mem.getTotal()) + " MiB"));
        document.add(new Paragraph("Available RAM: " + toMiB(mem.getAvailable()) + " MiB"));

        // Sensors
        document.add(new Paragraph("\n=== Sensors ==="));
        Sensors sensors = hal.getSensors();
        document.add(new Paragraph(String.format("CPU Temperature: %.1f °C", sensors.getCpuTemperature())));
        document.add(new Paragraph(String.format("CPU Voltage: %.2f V", sensors.getCpuVoltage())));

        int[] fanSpeeds = sensors.getFanSpeeds();
        if (fanSpeeds == null || fanSpeeds.length == 0) {
            document.add(new Paragraph("Fan speeds: not available on this system."));
        } else {
            Table fanTable = new Table(new float[]{1, 1}); 
            fanTable.useAllAvailableWidth();
            fanTable.addHeaderCell("Fan");
            fanTable.addHeaderCell("RPM");
            for (int i = 0; i < fanSpeeds.length; i++) {
                fanTable.addCell("Fan " + (i + 1));
                fanTable.addCell(String.valueOf(fanSpeeds[i]));
            }
            document.add(fanTable);
        }

        // Disks
        document.add(new Paragraph("\n=== Disks ==="));
        Table diskTable = new Table(new float[]{2, 3, 1, 1, 1}); 
        diskTable.useAllAvailableWidth();
        diskTable.addHeaderCell("Name");
        diskTable.addHeaderCell("Model");
        diskTable.addHeaderCell("Size(GB)");
        diskTable.addHeaderCell("Reads");
        diskTable.addHeaderCell("Writes");
        for (HWDiskStore disk : hal.getDiskStores()) {
            diskTable.addCell(disk.getName());
            diskTable.addCell(disk.getModel());
            diskTable.addCell(String.valueOf(disk.getSize() / (1024L * 1024 * 1024)));
            diskTable.addCell(String.valueOf(disk.getReads()));
            diskTable.addCell(String.valueOf(disk.getWrites()));
        }
        document.add(diskTable);

        // NVIDIA GPU
        document.add(new Paragraph("\n=== NVIDIA GPU ==="));
        List<String> nvidia = getNvidiaGpuInfo();
        if (nvidia.isEmpty()) {
            document.add(new Paragraph("NVIDIA GPU info not available (driver or nvidia-smi missing)."));
        } else {
            for (String gpu : nvidia) {
                document.add(new Paragraph(gpu));
            }
        }

        document.close();
        System.out.println("PDF report generated: " + pdfPath);
    }

    private static long toMiB(long bytes) {
        return bytes / (1024L * 1024L);
    }

    private static List<String> getNvidiaGpuInfo() {
        List<String> lines = new ArrayList<>();
        String[] cmd = new String[]{
                "nvidia-smi",
                "--query-gpu=name,temperature.gpu,utilization.gpu,memory.used,memory.total",
                "--format=csv,noheader,nounits"
        };
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String s;
                while ((s = br.readLine()) != null) {
                    String[] parts = s.split("\\s*,\\s*");
                    if (parts.length >= 5) {
                        lines.add(String.format("%s | Temp: %s °C | Util: %s %% | Mem: %s/%s MiB",
                                parts[0], parts[1], parts[2], parts[3], parts[4]));
                    }
                }
            }
            p.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception ignore) {}
        return lines;
    }
}
