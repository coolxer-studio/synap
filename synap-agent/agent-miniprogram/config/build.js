process.stderr.write(
  'Unsupported: the WeChat Mini Program agent does not implement secure protocol v1. ' +
  'No production artifact was generated.\n'
);
process.exitCode = 2;
