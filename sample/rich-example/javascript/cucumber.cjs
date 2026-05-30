// Cucumber-js configuration — wires ts-node so the .ts step-defs under
// features/step_definitions/ are loaded alongside the .js ones.
module.exports = {
    default: {
        requireModule: ['ts-node/register'],
        require: [
            'features/step_definitions/**/*.js',
            'features/step_definitions/**/*.ts',
        ],
        paths: ['features/**/*.feature'],
    },
};
