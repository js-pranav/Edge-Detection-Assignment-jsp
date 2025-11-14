#include "opencv-processor.h"
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <cstring>

std::vector<uint8_t> detectEdges(const uint8_t *imageData, int width, int height) {
    try {
        cv::Mat input(height + height / 2, width, CV_8UC1, const_cast<uint8_t *>(imageData));
        cv::Mat gray, edges;

        cv::cvtColor(input, gray, cv::COLOR_YUV2GRAY_NV21);

        cv::Mat blurred;
        cv::GaussianBlur(gray, blurred, cv::Size(5, 5), 1.5);

        cv::Canny(blurred, edges, 50.0, 150.0);

        cv::Mat output(height, width, CV_8UC4);
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                uint8_t edgeValue = edges.at<uint8_t>(y, x);
                output.at<cv::Vec4b>(y, x) = cv::Vec4b(edgeValue, edgeValue, edgeValue, 255);
            }
        }

        std::vector<uint8_t> result(output.total() * output.channels());
        std::memcpy(result.data(), output.data, result.size());

        return result;

    } catch (const std::exception &e) {
        return std::vector<uint8_t>();
    }
}
