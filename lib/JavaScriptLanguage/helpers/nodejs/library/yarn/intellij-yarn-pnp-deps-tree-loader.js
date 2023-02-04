// Prints dependencies information
// https://next.yarnpkg.com/advanced/pnpapi#traversing-the-dependency-tree

if (!process.versions.pnp) {
  console.error('Error: process.versions.pnp not found: ' + JSON.stringify(process.versions, null, 2));
}

const pnp = (() => {
  const module = require('module');
  if (typeof module.findPnpApi === 'function') {
    return module.findPnpApi(process.cwd());
  }
  return require('pnpapi');
})();

const createWorkspaceTraverser = (workspace) => {
  const visited = new Set();
  return function traverseDependencyTree(packageLocator, depth) {
    const packageLocatorJson = JSON.stringify(packageLocator);
    if (visited.has(packageLocatorJson)) {
      return;
    }
    visited.add(packageLocatorJson);
    const pkg = pnp.getPackageInformation(packageLocator);
    if (pkg == null) {
      console.error(pkg, 'Unavailable package information for ' + JSON.stringify(packageLocator));
      return;
    }
    if (depth === 1) {
      workspace.location = pkg.packageLocation;
      workspace.dependencies = [];
    }
    if (depth === 2) {
      addDependencyInfo(workspace.dependencies, packageLocator.name, pkg.packageLocation);
      return;
    }

    for (const [name, reference] of pkg.packageDependencies.entries())
      if (reference !== null) {
        traverseDependencyTree({name, reference}, depth + 1);
      }
  };
};

function addDependencyInfo(dependencies, dependencyName, dependencyRequireableLocation) {
  const info = {
    name: dependencyName,
    requireableLocation: dependencyRequireableLocation
  };
  if (typeof pnp.resolveVirtual === 'function') {
    const resolvedVirtual = pnp.resolveVirtual(dependencyRequireableLocation);
    if (resolvedVirtual && resolvedVirtual !== dependencyRequireableLocation) {
      info.resolvedVirtualRequireableLocation = resolvedVirtual;
    }
  }
  dependencies.push(info);
}

function fetchWorkspaces() {
  const workspaces = [];
  if (typeof pnp.getDependencyTreeRoots === 'function') {
    for (const workspaceRoot of pnp.getDependencyTreeRoots()) {
      const workspace = {};
      const traverseDependencyTree = createWorkspaceTraverser(workspace);
      traverseDependencyTree(workspaceRoot, 1);
      workspaces.push(workspace);
    }
  }
  else {
    const workspace = {};
    traverseDependencyTree_v1(workspace);
    workspaces.push(workspace);
  }
  return workspaces;
}

function traverseDependencyTree_v1(workspace) {
  const messagePrefix = '[Yarn PnP legacy API] ';
  workspace.location = process.cwd();
  workspace.dependencies = [];

  function addDeps(dependencies) {
    const issuer = process.cwd() + '/';
    for (const dependencyName of Object.keys(dependencies)) {
      try {
        addDependencyInfo(workspace.dependencies, dependencyName, pnp.resolveToUnqualified(dependencyName, issuer));
      }
      catch (e) {
        console.error(messagePrefix + 'Failed resolveToUnqualified(' + dependencyName + ', ' + issuer + ')', e);
      }
    }
  }

  let packageJson;
  try {
    const packageJsonPath = require('path').join(process.cwd(), 'package.json');
    packageJson = require(packageJsonPath);
  }
  catch (e) {
    console.error(messagePrefix + ' Cannot find ./package.json');
    throw e;
  }
  addDeps(packageJson.dependencies || {});
  addDeps(packageJson.devDependencies || {});
}

const deps = fetchWorkspaces();
const result = {
  environment: {
    'process.versions': {
      node: process.versions.node,
      pnp: process.versions.pnp
    },
    'pnp.VERSIONS': pnp.VERSIONS,
    'pnp.resolveVirtual': typeof pnp.resolveVirtual === 'function'
  },
  workspaces: deps
};

process.stdout.write(
    "##intellij-yarn-pnp-deps-tree-start\n" +
    JSON.stringify(result, null, 2) +
    "\n##intellij-yarn-pnp-deps-tree-end\n"
);
