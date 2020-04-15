layout (constant_id = 0) const int MAX_POINT_LIGHTS = 32;
layout (constant_id = 1) const int MAX_SPOT_LIGHTS = 32;
layout (constant_id = 2) const int MAX_DIRECTIONAL_LIGHTS = 32;

struct AmbientLight {
    vec3 color;
};

struct PointLight {
    vec3 viewPosition;
    vec3 color;
    float intensity;
    float attenuationConstant;
    float attenuationLinear;
    float attenuationQuadratic;
};

struct DirectionalLight {
    vec3 viewDirection;
    vec3 color;
    float intensity;
};

struct SpotLight {
    vec3 viewPosition;
    float angle;
    vec3 viewDirection;
    vec3 color;
    float intensity;
};

layout(binding = 3) uniform LightBuffer {
    AmbientLight ambientLight;
    PointLight pointLights[MAX_POINT_LIGHTS];
    SpotLight spotLights[MAX_SPOT_LIGHTS];
    DirectionalLight directionalLights[MAX_DIRECTIONAL_LIGHTS];
} lights;