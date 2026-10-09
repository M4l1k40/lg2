const HOME_PATH_BY_ROLE = [
  ['admin', '/admin/dashboard'],
  ['lawyer', '/lawyer/dashboard'],
  ['secretary', '/secretary/dashboard'],
  ['client', '/client/dashboard']
];

export function getHomePath(roles = []) {
  const match = HOME_PATH_BY_ROLE.find(([role]) => roles.includes(role));
  return match ? match[1] : null;
}
