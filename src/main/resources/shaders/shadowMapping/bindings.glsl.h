layout (constant_id = 3) const int MAX_SHADOW_MAPS = 16;
layout(binding = 5) uniform sampler2D shadowMaps[MAX_SHADOW_MAPS];

struct CameraInfo {
    mat4 projection;
    mat4 view;
};

layout(binding = 0, set = 1) uniform Matrix {
    CameraInfo matrices[MAX_SHADOW_MAPS];
} worldToProjectedMat;
